package util

import anorm.Id
import models.{AssetMeta, IpmiInfo}

object AttributeResolver extends MessageHelper("attributeresolver") {

  type IpmiTuple = Tuple2[IpmiInfo.Enum, String]
  type AssetMetaTuple = Tuple2[AssetMeta, String]
  type ResultTuple = Tuple3[Seq[IpmiTuple], Seq[AssetMetaTuple], Seq[String]]

  def apply(map: Map[String,String]): ResultTuple = {
    val init: ResultTuple = (Seq[IpmiTuple](), Seq[AssetMetaTuple](), Seq[String]())
    map.foldLeft(init) { case(total, kv) =>
      val (k, v) = kv
      val (ipmi, meta, address) = total
      asIpmi(k) match {
        case None => asAssetMeta(k) match {
          case None => isIpAddress(k) match {
            case true =>
              (ipmi, meta, (address ++ Seq(v)))
            case false =>
              throw new Exception(message("notfound", k))
          }
          case Some(assetMeta) =>
            (ipmi, ((assetMeta -> v) +: meta), address)
        }
        case Some(ipmiInfo) =>
          (((ipmiInfo -> v) +: ipmi), meta, address)
      }
    }
  }

  private def asIpmi(key: String): Option[IpmiInfo.Enum] = try {
    Some(IpmiInfo.Enum.withName(key))
  } catch {
    case _ => None
  }
  private def asAssetMeta(key: String): Option[AssetMeta] = try {
    val am = AssetMeta.Enum.withName(key)
    Some(AssetMeta(am.toString, -1, "label", "description", am.id))
  } catch {
    case _ => // If an exception was thrown, try the database
      AssetMeta.findByName(key)
  }
  private def isIpAddress(key: String): Boolean = {
    val lc = key.toLowerCase
    lc == "ip_address" || lc == "sl_ip_address" || lc == "sl_primary_ip_address"
  }
}
