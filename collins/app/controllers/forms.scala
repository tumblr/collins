package controllers

import models.{AssetType,Status,Truthy}
import util.views.Formatter.camelCase

import com.tumblr.play.{Power, PowerAction}

import play.api.data.FormError
import play.api.data.format._

import scala.util.control.Exception.allCatch

package object forms {

  implicit def statusFormat = new Formatter[Status.Enum] {
    def bind(key: String, data: Map[String, String]) = {
      Formats.stringFormat.bind(key, data).right.flatMap { s =>
        allCatch[Status.Enum]
          .either(Status.Enum.withName(camelCase(s)))
          .left.map(e => Seq(FormError(key, "error.status", Nil)))
      }
    }
    def unbind(key: String, value: Status.Enum) = Map(key -> value.toString)
  }

  implicit def typeFormat = new Formatter[AssetType.Enum] {
    def bind(key: String, data: Map[String, String]) = {
      Formats.stringFormat.bind(key, data).right.flatMap { s =>
        allCatch[AssetType.Enum]
          .either(AssetType.Enum.withName(s))
          .left.map(e => Seq(FormError(key, "error.assetType", Nil)))
      }
    }
    def unbind(key: String, value: AssetType.Enum) = Map(key -> value.toString)
  }

  implicit def powerFormat = new Formatter[PowerAction] {
    def bind(key: String, data: Map[String, String]) = {
      Formats.stringFormat.bind(key, data).right.flatMap { s =>
        allCatch[PowerAction]
          .either(Power(s))
          .left.map(e => Seq(FormError(key, "error.power", Nil)))
      }
    }
    def unbind(key: String, value: PowerAction) = Map(key -> value.toString)
  }

  implicit def truthyFormat = new Formatter[Truthy] {
    def bind(key: String, data: Map[String, String]) = {
      Formats.stringFormat.bind(key, data).right.flatMap { s =>
        allCatch[Truthy]
          .either(Truthy(s, true))
          .left.map(e => Seq(FormError(key, "error.truthy", Nil)))
      }
    }
    def unbind(key: String, value: Truthy) = Map(key -> value.toString)
  }

}
