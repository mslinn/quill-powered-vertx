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

package com.micronautics.vertx

import com.typesafe.config.Config
import io.vertx.core.Handler
import io.vertx.core.json.{JsonArray, JsonObject}
import io.vertx.ext.jdbc.{JDBCClient => JJDBCClient}
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.http.HttpServerResponse
import io.vertx.scala.ext.jdbc.JDBCClient
import io.vertx.scala.ext.sql.SQLConnection
import io.vertx.scala.ext.web.handler.BodyHandler
import io.vertx.scala.ext.web.{Router, RoutingContext}
import model.persistence._
import org.h2.tools.Server
import scala.collection.JavaConverters._

object Main extends App {
  QuillPoweredVertx()
}

object QuillPoweredVertx extends ConfigParse with PersistenceLike {
  def apply(): Unit = new QuillPoweredVertx().start()
}

class QuillPoweredVertx extends ScalaVerticle with Routes with RouteHandlers {
  vertx = Vertx.vertx()

  private var _client: Option[JDBCClient] = None

  def client: JDBCClient = _client.getOrElse {
    val result = JDBCClient(JJDBCClient.create(vertx.asJava.asInstanceOf[io.vertx.core.Vertx], Ctx.dataSource))
    _client = Some(result)
    result
  }

  override def start(): Unit = {
    setUpInitialData(_ => {
      val router = Router.router(vertx)
      router.route.handler(BodyHandler.create)

      router.route(s"$routeStem*").handler(routingContext =>
        client.getConnection(res => {
          if (res.failed) {
            routingContext.fail(res.cause)
          } else {
            val conn: SQLConnection = res.result

            // save the connection on the context
            routingContext.put("conn", conn)

            // The connection must be closed in order to return it to the JDBC pool.
            routingContext.addHeadersEndHandler(_ => conn.close(_ => {}))
            routingContext.next()
          }
        })
      ).failureHandler(routingContext => {
        val conn: SQLConnection = routingContext.get("conn")
        if (conn != null)
          conn.close(_ => {})
      })

      router.get(s"$routeStem/:productID").handler(handleGetProduct)
      router.post(routeStem).handler(handleAddProduct)
      router.get(routeStem).handler(handleListProducts)

      vertx
        .createHttpServer
        .requestHandler(router.accept(_))
        .listen(8080)
    })
  }

  // todo use evolutions instead
  protected def setUpInitialData(done: Handler[Unit]): Unit = {
    client.getConnection(res => {
      if (res.failed)
        throw new RuntimeException(res.cause)

      val conn: SQLConnection = res.result

      // wipe out any previous data
      conn.execute("DELETE FROM products", ddl => { if (ddl.failed) throw new RuntimeException(ddl.cause) })

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

protected trait PersistenceLike {
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
}

protected trait Routes {
  val routeStem = "/products"
}

protected trait RouteHandlers {
  // todo use quill instead of raw sql
  protected def handleGetProduct(routingContext: RoutingContext): Unit = {
    val productID: Option[String] = routingContext.request.getParam("productID")
    val response: HttpServerResponse = routingContext.response
    if (productID.isEmpty) {
      sendError(400, response)
    } else productID.map { id =>
      val conn: SQLConnection = routingContext.get("conn")
      conn.queryWithParams(
        "SELECT id, name, price, weight FROM products where id = ?",
        new JsonArray().add(Integer.parseInt(id)),
        query => {
          if (query.failed()) {
            sendError(500, response)
          } else {
            if (query.result.asJava.getNumRows == 0) {
              sendError(404, response)
            } else {
              response
                .putHeader("content-type", "application/json")
                .end(query.result.asJava.getRows.get(0).encode)
            }
          }
        }
      )
    }.get
  }

  // todo use quill instead of raw sql
  protected def handleAddProduct(routingContext: RoutingContext): Unit = {
    val response: HttpServerResponse = routingContext.response
    val conn: SQLConnection = routingContext.get("conn")
    routingContext.getBodyAsJson foreach { product: JsonObject =>
      conn.updateWithParams("INSERT INTO products (name, price, weight) VALUES (?, ?, ?)",
        new JsonArray()
          .add(product.getString("name"))
          .add(product.getFloat("price"))
          .add(product.getInteger("weight")),
        query => {
          if (query.failed)
            sendError(500, response)
          else
            response.end
        }
      )
    }
  }

  // todo use quill instead of raw sql
  protected def handleListProducts(routingContext: RoutingContext): Unit = {
    val response: HttpServerResponse = routingContext.response
    val conn: SQLConnection = routingContext.get("conn")
    conn.query("SELECT id, name, price, weight FROM products", query => {
      if (query.failed) {
        sendError(500, response)
      } else {
        val arr: JsonArray = new JsonArray()
        query.result.asJava.getRows.asScala.foreach(arr.add)
        routingContext.response.putHeader("content-type", "application/json").end(arr.encodePrettily)
      }
    })
  }

  protected def sendError(statusCode: Int, response: HttpServerResponse): Unit =
    response
      .setStatusCode(statusCode)
      .end()
}
