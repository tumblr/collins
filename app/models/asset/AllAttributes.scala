package models.asset

import models._
import util.{LldpRepresentation, LshwRepresentation}
import util.config.{Feature, MultiCollinsConfig, NodeclassifierConfig}
import util.power.PowerUnits

import play.api.libs.json._

object AllAttributes {
  def get(asset: Asset): AllAttributes = {
    if (asset.isConfiguration) {
      AllAttributes(asset,
        LshwRepresentation.empty,
        LldpRepresentation.empty,
        None,
        IpAddresses.findAllByAsset(asset),
        PowerUnits(),
        AssetMetaValue.findByAsset(asset)
      )
    } else {
      val (lshwRep, mvs) = LshwHelper.reconstruct(asset)
      val (lldpRep, mvs2) = LldpHelper.reconstruct(asset, mvs)
      val ipmi = IpmiInfo.findByAsset(asset)
      val addresses = IpAddresses.findAllByAsset(asset)
      val (powerRep, mvs3) = PowerHelper.reconstruct(asset, mvs2)
      val filtered: Seq[MetaWrapper] = mvs3.filter(f => !Feature.hideMeta.contains(f.getName))
      AllAttributes(asset, lshwRep, lldpRep, ipmi, addresses, powerRep, filtered)
    }
  }
}

case class AllAttributes(
  asset: Asset,
  lshw: LshwRepresentation,
  lldp: LldpRepresentation,
  ipmi: Option[IpmiInfo],
  addresses: Seq[IpAddresses],
  power: PowerUnits,
  mvs: Seq[MetaWrapper])
{

  import models.conversions._
  import util.power.PowerUnit.PowerUnitFormat

  def exposeCredentials(showCreds: Boolean = false) = {
    this.copy(ipmi = this.ipmi.map { _.withExposedCredentials(showCreds) })
        .copy(mvs = this.metaValuesWithExposedCredentials(showCreds))
  }

  protected def metaValuesWithExposedCredentials(showCreds: Boolean): Seq[MetaWrapper] = {
    if (showCreds) {
      mvs
    } else {
      mvs.filter(mv => !Feature.encryptedTags.map(_.name).contains(mv.getName))
    }
  }

  def toJsValue(): JsValue = {
    val outSeq = Seq(
      "ASSET" -> asset.toJsValue,
      "HARDWARE" -> lshw.toJsValue,
      "LLDP" -> lldp.toJsValue,
      "IPMI" -> Json.toJson(ipmi),
      "ADDRESSES" -> Json.toJson(addresses),
      "POWER" -> Json.toJson(power),
      "ATTRIBS" -> JsObject(mvs.groupBy { _.getGroupId }.map { case(groupId, mv) =>
        groupId.toString -> JsObject(mv.map { mvw => mvw.getName -> JsString(mvw.getValue) })
      }.toSeq)
    )
    JsObject(outSeq)
  }
}
