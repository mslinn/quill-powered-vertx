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

package com.micronautics.vertx.auth

import com.micronautics.sig.JWT
import io.vertx.lang.scala.json.JsonObject
import io.vertx.scala.core.Vertx
import io.vertx.scala.ext.auth.jwt.{JWTAuth, JWTAuthOptions, JWTOptions}
import io.vertx.scala.ext.auth.{AuthProvider, KeyStoreOptions, User}
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/** @see See [[http://vertx.io/docs/vertx-auth-jwt/scala/ The JWT auth provider]] in the Vert.x documentation */
object Auth {
  val keyStoreOptions: KeyStoreOptions =
    KeyStoreOptions()
      .setPath("keystore.jceks")
      .setPassword("secret")

  val config: JWTAuthOptions =
    JWTAuthOptions()
      .setKeyStore(keyStoreOptions)
}

class Auth(vertx: Vertx) {
  import com.micronautics.vertx.auth.Auth._

  lazy val provider: JWTAuth = JWTAuth.create(vertx, config)

  /** For any request to protected resources, pass the returned value from this method in the HTTP `Authorization` header as:
    * {{{Authorization: Bearer <token>}}}
    * @see See [[https://en.wikipedia.org/wiki/JSON_Web_Token#Standard_fields Standard JWT Fields]] */
  def tokenFrom(userName: String, password: String): JWT = {
    val jsonObject = new JsonObject().put("sub", userName)
    val token: JWT = provider.generateToken(jsonObject, JWTOptions())
    token
  }

  // todo figure out how this stuff works. No docs of any kind anywhere, AFAIK!
  def authenticatedUser(bearerToken: JWT, authProvider: AuthProvider): Option[User] =
    try {
      val user = Await.result(authProvider.authenticateFuture(new JsonObject().put("jwt", "BASE64-ENCODED-STRING")), Duration.Inf)
      //if (user.isAuthorised("whatGoesHere?", "whatGoesHere?")) Some(user) else None  // todo figure this out
      None // make compiler happy for now
    } catch {
      case _: Exception => None
    }
}
