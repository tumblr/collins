package models

import util.CryptoCodec

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
  def getValue(): String = AssetMetaValueConfig.EncryptedMeta.contains(getName) match {
    case true => CryptoCodec.withKeyFromFramework.Decode(_value.value).getOrElse(_value.value)
    case false => _value.value
  }
  override def toString(): String = getValue()
  def valueEquals(m: MetaWrapper) = getValue() == m.getValue()
}

object MetaWrapper {
  def apply(amv: AssetMetaValue): MetaWrapper = MetaWrapper(amv.getMeta, amv)
  def createMeta(asset: Asset, metas: Map[String,String], groupId: Option[Int] = None) = {
    val metaValues = metas.map { case(k,v) =>
      val meta = AssetMeta.findOrCreateFromName(k)
      groupId.map(AssetMetaValue(asset, meta.id, _, v))
        .getOrElse(AssetMetaValue(asset, meta.id, v))
    }.toSeq
    AssetMetaValue.purge(metaValues, groupId)
    val values = metaValues.filter(v => v.value != null && v.value.nonEmpty)
    values.size match {
      case 0 =>
      case n =>
        AssetMetaValue.create(values)
    }
  }

}
