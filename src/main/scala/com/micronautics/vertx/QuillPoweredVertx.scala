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

import _root_.model.persistence._
import com.micronautics.vertx.model.{Product, Products}
import com.typesafe.config.Config
import io.vertx.core.json.{JsonArray, JsonObject}
import io.vertx.ext.jdbc.{JDBCClient => JJDBCClient}
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.http.HttpServerResponse
import io.vertx.scala.ext.jdbc.JDBCClient
import io.vertx.scala.ext.sql.SQLConnection
import io.vertx.scala.ext.web.handler.BodyHandler
import io.vertx.scala.ext.web.{Router, RoutingContext}
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
    val router = Router.router(vertx)
    router.route.handler(BodyHandler.create)

    router.get(s"$routeStem/:productID").handler(handleGetProduct)
    router.post(routeStem).handler(handleAddProduct)
    router.get(routeStem).handler(handleListProducts)

    vertx
      .createHttpServer
      .requestHandler(router.accept(_))
      .listen(8080)
  }
}

protected trait PersistenceLike {
  protected val h2Config: Config = ConfigParse.config.getConfig("h2")
  protected val dataSource: Config = h2Config.getConfig("dataSource")
  protected val url: String = dataSource.getString("url")
  protected val h2Server: Server = org.h2.tools.Server.createTcpServer("-baseDir", "./h2data")
  h2Server.start()

  val resourcePath = "evolutions/1.sql" // for accessing evolution file as a resource from a jar
  val fallbackPath = s"src/main/resources/$resourcePath" // for testing this project
  val processEvolution = new ProcessEvolution(resourcePath, fallbackPath)

  val downsLines: Seq[String] = processEvolution.downsLines(Ctx)
  val upsLines: Seq[String]   = processEvolution.upsLines(Ctx)

  processEvolution.downs(Ctx) // just in case something was left over from last time
  processEvolution.ups(Ctx)
}

protected trait Routes {
  val routeStem = "/products"
}

protected trait RouteHandlers extends IdImplicitLike {
  protected def handleGetProduct(routingContext: RoutingContext): Unit = {
    val response: HttpServerResponse = routingContext.response
    val result: Option[Unit] = for {
      id      <- routingContext.request.getParam("productID")
      product <- Products._findById(id.toId)
    } yield {
      response
        .putHeader("content-type", "application/json")
        .end(product.toString)
    }
    result.getOrElse(sendError(400, response))
  }

  protected def handleAddProduct(routingContext: RoutingContext): Unit = {
    val response: HttpServerResponse = routingContext.response
    val result: Option[Unit] = for {
      jsonObject: JsonObject <- routingContext.getBodyAsJson
    } yield {
      val product = Product(
        name = jsonObject.getString("name"),
        price = jsonObject.getString("price"),
        weight = jsonObject.getDouble("weight")
      )
      Products._insert(product)
      response.end(s"Inserted product: $product")
    }
    result.getOrElse(sendError(500, response))
  }

  protected def handleListProducts(routingContext: RoutingContext): Unit = {
    val response: HttpServerResponse = routingContext.response
    val jsonProducts: Seq[String] = for {
      product <- Products._findAll
    } yield product.toJson
    val json = s"[\n  ${ jsonProducts.mkString(",\n  ") }\n]"
    if (jsonProducts.isEmpty)
      sendError(500, response)
    else
      response
        .putHeader("content-type", "application/json")
        .end(json)
  }

  protected def sendError(statusCode: Int, response: HttpServerResponse): Unit =
    response
      .setStatusCode(statusCode)
      .end()
}
