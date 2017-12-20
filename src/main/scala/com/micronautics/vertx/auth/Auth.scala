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

import io.vertx.lang.scala.json.JsonObject
import io.vertx.scala.core.Vertx
import io.vertx.scala.ext.auth.KeyStoreOptions
import io.vertx.scala.ext.auth.jwt.{JWTAuth, JWTAuthOptions, JWTOptions}
import scala.sys.process.{Process,ProcessBuilder}

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

/*
keytool -genseckey -keystore keystore.jceks -storetype jceks -storepass secret -keyalg HMacSHA256 -keysize 2048 -alias HS256 -keypass secret
keytool -genseckey -keystore keystore.jceks -storetype jceks -storepass secret -keyalg HMacSHA384 -keysize 2048 -alias HS384 -keypass secret
keytool -genseckey -keystore keystore.jceks -storetype jceks -storepass secret -keyalg HMacSHA512 -keysize 2048 -alias HS512 -keypass secret
keytool -genkey -keystore keystore.jceks -storetype jceks -storepass secret -keyalg RSA -keysize 2048 -alias RS256 -keypass secret -sigalg SHA256withRSA -dname "CN=,OU=,O=,L=,ST=,C=" -validity 360
keytool -genkey -keystore keystore.jceks -storetype jceks -storepass secret -keyalg RSA -keysize 2048 -alias RS384 -keypass secret -sigalg SHA384withRSA -dname "CN=,OU=,O=,L=,ST=,C=" -validity 360
keytool -genkey -keystore keystore.jceks -storetype jceks -storepass secret -keyalg RSA -keysize 2048 -alias RS512 -keypass secret -sigalg SHA512withRSA -dname "CN=,OU=,O=,L=,ST=,C=" -validity 360
keytool -genkeypair -keystore keystore.jceks -storetype jceks -storepass secret -keyalg EC -keysize 256 -alias ES256 -keypass secret -sigalg SHA256withECDSA -dname "CN=,OU=,O=,L=,ST=,C=" -validity 360
keytool -genkeypair -keystore keystore.jceks -storetype jceks -storepass secret -keyalg EC -keysize 384 -alias ES384 -keypass secret -sigalg SHA384withECDSA -dname "CN=,OU=,O=,L=,ST=,C=" -validity 360
keytool -genkeypair -keystore keystore.jceks -storetype jceks -storepass secret -keyalg EC -keysize 521 -alias ES512 -keypass secret -sigalg SHA512withECDSA -dname "CN=,OU=,O=,L=,ST=,C=" -validity 360
*/
object BearerToken {
  implicit def stringToBearerToken(string: String): BearerToken = BearerToken(string)

  def run(cmd: String*): String = Process(cmd).!!.trim

  def keytool(storeSecret: String, keySecret: String): String =
    run("keytool",
      "-genseckey",
      "-keystore", "keystore.jceks",
      "-storetype", "jceks",
      "-storepass", storeSecret,
      "-keyalg", "HMacSHA256",
      "-keysize", "2048",
      "-alias", "HS256",
      "-keypass", keySecret)
}

case class BearerToken(value: String) extends AnyVal

class Auth(vertx: Vertx) {
  import com.micronautics.vertx.auth.Auth._

  val provider: JWTAuth = JWTAuth.create(vertx, config)

  /** For any request to protected resources, pass the returned value from this method in the HTTP `Authorization` header as:
    * {{{Authorization: Bearer <token>}}}
    * @see See [[https://en.wikipedia.org/wiki/JSON_Web_Token#Standard_fields Standard JWT Fields]] */
  def tokenFrom(userName: String, password: String): BearerToken = {
    val jsonObject = new JsonObject().put("sub", userName)
    val token: BearerToken = provider.generateToken(jsonObject, JWTOptions())
    token
  }

  def authenticatedUser(bearerToken: BearerToken): Option[User] =
    bearerToken.value.authenticateFuture(new io.vertx.core.json.JsonObject().put("jwt", "BASE64-ENCODED-STRING")).onComplete{
    case Success(result) => {
      var theUser = result
    }
    case Failure(cause) => {
      println(s"$cause")
    }
  }
}
