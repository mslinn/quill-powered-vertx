package com.micronautics.vertx.model

import model.persistence.Types.{IdOptionLong, OptionLong}
import model.persistence._
import com.micronautics.vertx._

case class Product(name: String, price: String, weight: Double, override val id: Id[Option[Long]] = Id.empty)
  extends HasId[Product, Option[Long]] {

  @inline override lazy val toString: String = s"id=$id; name=$name; price=$price; weight=$weight"

  @inline lazy val toJson: String = s"""{"name"="$name","price"="$price","weight"="$weight","id"="$id"}"""
}

object Products extends UnCachedPersistence[Long, OptionLong, Product] {
  import com.micronautics.vertx.Ctx._

  @inline def _findAll: List[Product] = run { quote { query[Product] } }

  val queryById: (IdOptionLong) => Ctx.Quoted[Ctx.EntityQuery[Product]] =
    (id: IdOptionLong) =>
      quote { query[Product].filter(_.id == lift(id)) }

  override val _deleteById: (IdOptionLong) => Unit =
    (id: IdOptionLong) => {
      run { quote { queryById(id).delete } }
      ()
    }

  override val _findById: (IdOptionLong) => Option[Product] =
    (id: IdOptionLong) =>
      run { quote { queryById(id) } }.headOption

  val _insert: Product => Product =
    (product: Product) => {
      val id: IdOptionLong = try {
        run { quote { query[Product].insert(lift(product)) }.returning(_.id) }
      } catch {
        case e: Throwable =>
          logger.error(e.getMessage)
          throw e
      }
      product.copy(id=id)
    }

  val _update: Product => Product =
    (Product: Product) => {
      run { queryById(Product.id).update(lift(Product)) }
      Product
    }

  @inline override def findById(id: Id[Option[Long]]): Option[Product] =
    run { queryById(id) }.headOption
}
