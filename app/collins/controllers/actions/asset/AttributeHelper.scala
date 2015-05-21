package collins.controllers.actions.asset

import scala.collection.immutable.DefaultMap

import play.api.mvc.AnyContent
import play.api.mvc.Request

import collins.controllers.actions.SecureAction

case class AttributeMap(underlying: Map[String,String]) extends DefaultMap[String,String] {
  override def get(key: String): Option[String] = underlying.get(key)
  override def iterator: Iterator[(String,String)] = underlying.iterator
}

object AttributeMap {
  def apply(seq: Seq[String]) = new AttributeMap(
    seq.map(_.split(";", 2))
      .filter(a => a.length > 1)
      .map(s => s(0) -> s(1))
      .toMap
  )
  def fromMap(map: Map[String,Seq[String]]): Option[Seq[String]] = {
    map.get("attribute")
  }
}

trait AttributeHelper {

  protected def invalidAttributeMessage(param: String): String

  protected def mapAttributes(first: Option[Seq[String]], second: Option[Seq[String]]): Map[String, String] = {
    val attribs = first.orElse(second).filter(_.nonEmpty)
    attribs.foreach { list =>
      list.find(!_.contains(";")).headOption.foreach { s =>
        throw new Exception(invalidAttributeMessage(s))
      }
    }
    AttributeMap(attribs.map(_.toList).getOrElse(List()))
  }
}

object ActionAttributeHelper {
  def getInputMapFromRequest(request: Request[AnyContent]): Map[String,Seq[String]] = request.queryString ++ (request.body match {
    case b: play.api.mvc.AnyContent if b.asFormUrlEncoded.isDefined => b.asFormUrlEncoded.get
    case b: play.api.mvc.AnyContent if b.asMultipartFormData.isDefined => b.asMultipartFormData.get.asFormUrlEncoded
    case b: Map[_, _] => b.asInstanceOf[Map[String, Seq[String]]]
    case _ => Map.empty[String, Seq[String]]
  })
}

trait ActionAttributeHelper extends AttributeHelper {
  self: SecureAction =>

  protected def getAttributeMap: AttributeMap = {
    val map = mapAttributes(
      AttributeMap.fromMap(getInputMap),
      AttributeMap.fromMap(request.queryString)
    )
    AttributeMap(map)
  }

  protected def getInputMap: Map[String,Seq[String]] =
    ActionAttributeHelper.getInputMapFromRequest(request)
}
