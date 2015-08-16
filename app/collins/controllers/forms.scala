package collins.controllers

import scala.util.control.Exception.allCatch

import play.api.data.FormError
import play.api.data.format.Formats
import play.api.data.format.Formatter

import collins.models.Asset
import collins.models.AssetSort
import collins.models.AssetType
import collins.models.AssetType
import collins.models.State
import collins.models.State
import collins.models.Status
import collins.models.Status
import collins.models.Truthy
import collins.power.PowerAction
import collins.solr.AssetDocType
import collins.solr.CollinsQueryParser
import collins.solr.SolrExpression
import collins.solr.CQLQuery

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

  implicit def sortTypeformat = new Formatter[AssetSort.Type] {
    def bind(key: String, data: Map[String, String]) = {
      Formats.stringFormat.bind(key, data).right.flatMap { s =>
        allCatch[AssetSort.Type]
          .either(AssetSort.withName(s))
          .left.map(e => Seq(FormError(key, "sorttype.invalid", Nil)))
      }
    }
    def unbind(key: String, value: AssetSort.Type) = Map(key -> value.toString)
  }

  def parseQuery(qry: String): Either[Throwable, SolrExpression] = {
    CollinsQueryParser(List(AssetDocType)).parseQuery(qry) match {
      case Left(_) if Asset.isValidTag(qry) => CollinsQueryParser(List(AssetDocType)).parseQuery("tag=%s".format(qry)) match {
        case Left(err: String)  => throw new IllegalArgumentException("Invalid cql %s".format(err))
        case Right(q: CQLQuery) => Right(q.where)
      }
      case Left(err: String)  => throw new IllegalArgumentException("Invalid cql %s".format(err))
      case Right(q: CQLQuery) => Right(q.where)
    }
  }

  implicit def SolrExpressionFormat = new Formatter[SolrExpression] {
    def bind(key: String, data: Map[String, String]) = {
      Formats.stringFormat.bind(key, data).right.flatMap { qry =>
        allCatch[SolrExpression]
          .either(parseQuery(qry.trim).right.get)
          .left.map(_ => Seq(FormError(key, "query.invalid", Nil)))
      }
    }
    def unbind(key: String, value: SolrExpression) = Map(key -> value.toString)
  }


}
