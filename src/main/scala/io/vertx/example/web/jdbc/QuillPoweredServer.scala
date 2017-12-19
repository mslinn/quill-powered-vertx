/*
 * Copyright 2017 Micronautics Research Corporation.
 * Portions Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.example.web.jdbc

import com.typesafe.config.Config
import io.vertx.core.{AbstractVerticle, Handler, Vertx}
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.{JsonArray, JsonObject}
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.sql.SQLConnection
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.{Router, RoutingContext}
import model.persistence._
import org.h2.tools.Server

trait SelectedCtx extends model.persistence.H2Ctx

case object Ctx extends SelectedCtx with QuillCacheImplicits

object QuillPoweredServer extends App with ConfigParse {
  protected val h2Config: Config = model.persistence.ConfigParse.config.getConfig("h2")
  protected val dataSource: Config = h2Config.getConfig("dataSource")
  protected val url: String = dataSource.getString("url")
  protected val h2Server: Server = org.h2.tools.Server.createTcpServer("-baseDir", "./h2data")
  h2Server.start()

  val resourcePath = "evolutions/1.sql" // for accessing evolution file as a resource from a jar
  val fallbackPath = s"src/main/resources/$resourcePath" // for testing this project
  val processEvolution = new ProcessEvolution(resourcePath, fallbackPath)
  processEvolution.downs(Ctx) // just in case something was left over from last time
  processEvolution.ups(Ctx)

  new QuillPoweredServer().start()
}

/**
 * @author <a href="mailto:mslinn@gmail.com">Mike Slinn</a>
 */
class QuillPoweredServer extends AbstractVerticle {
  vertx = Vertx.vertx()

  private var _client: Option[JDBCClient] = None

  // Create a JDBC client with a test database
  def client: JDBCClient = _client.getOrElse {
    val result = JDBCClient.create(vertx, Ctx.dataSource)
    _client = Some(result)
    result
  }

  override def start(): Unit = {
    val thisServer: QuillPoweredServer = this

    setUpInitialData(_ => {
      val router = Router.router(vertx)
      router.route.handler(BodyHandler.create)

      // in order to minimize the nesting of call backs we can put the JDBC connection on the context for all routes
      // that match /products
      // this should really be encapsulated in a reusable JDBC handler that uses can just add to their app
      router.route("/products*").handler(routingContext => client.getConnection(res => {
        if (res.failed()) {
          routingContext.fail(res.cause)
        } else {
          val conn: SQLConnection = res.result

          // save the connection on the context
          routingContext.put("conn", conn)

          // we need to return the connection back to the jdbc pool. In order to do that we need to close it, to keep
          // the remaining code readable one can add a headers end handler to close the connection.
          routingContext.addHeadersEndHandler(_ => conn.close(_ => { }))
          routingContext.next()
        }
      })).failureHandler(routingContext => {
        val conn: SQLConnection = routingContext.get("conn")
        if (conn != null)
          conn.close(_ => {})
      })

      router.get("/products/:productID").handler(thisServer.handleGetProduct)
      router.post("/products").handler(thisServer.handleAddProduct)
      router.get("/products").handler(thisServer.handleListProducts)

      vertx
        .createHttpServer
        .requestHandler(router.accept(_))
        .listen(8080)
    })
  }

  private def handleGetProduct(routingContext: RoutingContext): Unit = {
    val productID: String = routingContext.request.getParam("productID")
    val response: HttpServerResponse = routingContext.response
    if (productID == null) {
      sendError(400, response)
    } else {
      val conn: SQLConnection = routingContext.get("conn")
      conn.queryWithParams(
        "SELECT id, name, price, weight FROM products where id = ?",
        new JsonArray().add(Integer.parseInt(productID)),
        query => {
          if (query.failed()) {
            sendError(500, response)
          } else {
            if (query.result.getNumRows == 0) {
              sendError(404, response)
            } else {
              response
                .putHeader("content-type", "application/json")
                .end(query.result.getRows.get(0).encode)
            }
          }
        }
      )
    }
  }

  private def handleAddProduct(routingContext: RoutingContext): Unit = {
    val response: HttpServerResponse = routingContext.response
    val conn: SQLConnection = routingContext.get("conn")
    val product: JsonObject = routingContext.getBodyAsJson

    conn.updateWithParams("INSERT INTO products (name, price, weight) VALUES (?, ?, ?)",
      new JsonArray()
        .add(product.getString("name"))
        .add(product.getFloat("price"))
        .add(product.getInteger("weight")),
      query => {
        if (query.failed()) {
          sendError(500, response)
        } else {
          response.end()
        }
      }
    )
  }

  private def handleListProducts(routingContext: RoutingContext): Unit = {
    import scala.collection.JavaConverters._

    val response: HttpServerResponse = routingContext.response
    val conn: SQLConnection = routingContext.get("conn")

    conn.query("SELECT id, name, price, weight FROM products", query => {
      if (query.failed) {
        sendError(500, response)
      } else {
        val arr: JsonArray = new JsonArray()
        query.result.getRows.asScala.foreach(arr.add)
        routingContext.response.putHeader("content-type", "application/json").end(arr.encodePrettily)
      }
    })
  }

  private def sendError(statusCode: Int, response: HttpServerResponse): Unit =
    response.setStatusCode(statusCode).end()

  private def setUpInitialData(done: Handler[Unit]): Unit = {
    client.getConnection(res => {
      if (res.failed)
        throw new RuntimeException(res.cause)

      val conn: SQLConnection = res.result

      conn.execute("CREATE TABLE IF NOT EXISTS products(id INT IDENTITY, name VARCHAR(255), price FLOAT, weight INT)", ddl => {
        if (ddl.failed)
          throw new RuntimeException(ddl.cause)

        conn.execute("INSERT INTO products (name, price, weight) VALUES ('Egg Whisk', 3.99, 150), ('Tea Cosy', 5.99, 100), ('Spatula', 1.00, 80)", fixtures => {
          if (fixtures.failed)
            throw new RuntimeException(fixtures.cause())

          done.handle(null)
        })
      })
    })
  }
}
