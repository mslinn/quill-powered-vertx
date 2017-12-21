package com.micronautics

import scala.language.reflectiveCalls

package object vertx {
  type Closeable = { def close(): Unit }

  def using[A <: Closeable, B](closeable: A)(f: A => B): B = {
    try {
      f(closeable)
    } finally {
      try {
        closeable.close()
      } catch { case _: Throwable => () }
    }
  }
}
