package collins.models.asset

import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.Json

import collins.util.Stats

import collins.models.Asset
import collins.models.AssetMetaValue
import collins.models.IpAddresses
import collins.models.IpmiInfo
import collins.models.LldpHelper
import collins.models.LshwHelper
import collins.models.MetaWrapper
import collins.models.PowerHelper
import collins.models.conversions.IpAddressFormat
import collins.models.conversions.IpmiFormat

import collins.util.LldpRepresentation
import collins.util.LshwRepresentation
import collins.util.config.Feature
import collins.util.power.PowerUnit.PowerUnitFormat
import collins.util.power.PowerUnits
import collins.util.power.PowerUnits

object AllAttributes {
  def get(asset: Asset): AllAttributes = Stats.time("Asset.GetAllAttributes") {
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

  import collins.models.conversions._
  import collins.util.power.PowerUnit.PowerUnitFormat

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

  def toJsValue(): JsValue = Stats.time("SerializeAsset.AllAttributes") {
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
