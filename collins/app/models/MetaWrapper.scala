package models

import util.Cache

import conversions._
import java.util.Date
/**
 * Provide a convenience wrapper on top of a row of meta/value data
 */
case class MetaWrapper(_meta: AssetMeta, _value: AssetMetaValue) {
  def getAssetId(): Long = _value.asset_id
  def getMetaId(): Long = _meta.id
  def getId(): (Long,Long) = (getAssetId(), getMetaId())
  def getName(): String = _meta.name
  def getGroupId(): Int = _value.group_id
  def getNameEnum(): Option[AssetMeta.Enum] = try {
    Some(AssetMeta.Enum.withName(getName()))
  } catch { case _ => None }
  def getPriority(): Int = _meta.priority
  def getLabel(): String = _meta.label
  def getDescription(): String = _meta.description
  def getValue(): String = _value.value
  override def toString(): String = getValue()
}
object MetaWrapper {
  def createMeta(asset: Asset, metas: Map[String,String]) = {
    val metaValues = metas.map { case(k,v) =>
      val metaName = k.toUpperCase
      val meta: AssetMeta = AssetMeta.findByName(metaName).getOrElse {
        AssetMeta.create(AssetMeta(metaName, -1, metaName.toLowerCase.capitalize, metaName))
        AssetMeta.findByName(metaName).get
      }
      AssetMetaValue(asset, meta.id, v)
    }.toSeq
    AssetMetaValue.purge(metaValues)
    val values = metaValues.filter(v => v.value != null && v.value.nonEmpty)
    values.size match {
      case 0 =>
      case n =>
        AssetMetaValue.create(values)
    }
  }

  def findMeta(asset: Asset, name: String, count: Int = 1): Seq[MetaWrapper] = {
    AssetMeta.findByName(name).map { meta =>
      AssetMetaValue.findByAssetAndMeta(asset, meta, count)
    }.getOrElse(Nil)
  }

  def findMeta(asset: Asset, name: String): Option[MetaWrapper] = {
    findMeta(asset, name, 1) match {
      case Nil => None
      case head :: Nil => Some(head)
    }
  }

}
