package controllers
import play.api.data.FormError
import play.api.data.format._
import models.{AssetType,Status}

package object forms {

  implicit def statusFormat = new Formatter[Status.Enum] {
    def bind(key: String, data: Map[String, String]) = {
      Formats.stringFormat.bind(key, data).right.flatMap { s =>
        scala.util.control.Exception.allCatch[Status.Enum]
          .either(Status.Enum.withName(s))
          .left.map(e => Seq(FormError(key, "error.status", Nil)))
      }
    }
    def unbind(key: String, value: Status.Enum) = Map(key -> value.toString)
  }
  implicit def typeFormat = new Formatter[AssetType.Enum] {
    def bind(key: String, data: Map[String, String]) = {
      Formats.stringFormat.bind(key, data).right.flatMap { s =>
        scala.util.control.Exception.allCatch[AssetType.Enum]
          .either(AssetType.Enum.withName(s))
          .left.map(e => Seq(FormError(key, "error.assetType", Nil)))
      }
    }
    def unbind(key: String, value: AssetType.Enum) = Map(key -> value.toString)
  }

}
