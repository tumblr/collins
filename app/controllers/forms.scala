package controllers

import models.{AssetType,State,Status,Truthy}
import util.views.Formatter.camelCase

import collins.power.PowerAction

import play.api.data.FormError
import play.api.data.format._

import scala.util.control.Exception.allCatch

import models.AssetSortType
import models.AssetSortType._

package object forms {

  implicit def statusFormat = new Formatter[Status] {
    def bind(key: String, data: Map[String, String]) = {
      Formats.stringFormat.bind(key, data).right.flatMap { s =>
        allCatch[Status]
          .either(Status.findByName(s).get)
          .left.map(e => Seq(FormError(key, "error.status", Nil)))
      }
    }
    def unbind(key: String, value: Status) = Map(key -> value.toString)
  }

  implicit def stateFormat = new Formatter[State] {
    def bind(key: String, data: Map[String, String]) = {
      Formats.stringFormat.bind(key, data).right.flatMap { s =>
        allCatch[State]
          .either(State.findByName(s).get)
          .left.map(e => Seq(FormError(key, "asset.state.invalid", Nil)))
      }
    }
    def unbind(key: String, value: State) = Map(key -> value.name)
  }

  implicit def typeFormat = new Formatter[AssetType] {
    def bind(key: String, data: Map[String, String]) = {
      Formats.stringFormat.bind(key, data).right.flatMap { s =>
        allCatch[AssetType]
          .either(AssetType.findByName(s).get)
          .left.map(e => Seq(FormError(key, "error.assetType", Nil)))
      }
    }
    def unbind(key: String, value: AssetType) = Map(key -> value.name)
  }

  implicit def powerFormat = new Formatter[PowerAction] {
    def bind(key: String, data: Map[String, String]) = {
      Formats.stringFormat.bind(key, data).right.flatMap { s =>
        allCatch[PowerAction]
          .either(PowerAction(s))
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

  implicit def sortTypeformat = new Formatter[AssetSortType] {
    def bind(key: String, data: Map[String, String]) = {
      Formats.stringFormat.bind(key, data).right.flatMap { s =>
        allCatch[AssetSortType]
          .either(AssetSortType.withName(s))
          .left.map(e => Seq(FormError(key, "sorttype.invalid", Nil)))
      }
    }
    def unbind(key: String, value: AssetSortType) = Map(key -> value.toString)
  }

}
