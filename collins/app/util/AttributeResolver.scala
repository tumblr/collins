package util

import anorm.Id
import models.{AssetMeta, IpmiInfo}

object AttributeResolver {

  type IpmiTuple = Tuple2[IpmiInfo.Enum, String]
  type AssetMetaTuple = Tuple2[AssetMeta, String]
  type ResultTuple = Tuple2[Seq[IpmiTuple], Seq[AssetMetaTuple]]

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

  def apply(map: Map[String,String]): ResultTuple = {
    val init: ResultTuple = (Seq[IpmiTuple](), Seq[AssetMetaTuple]())
    map.foldLeft(init) { case(total, kv) =>
      val (k, v) = kv
      val (ipmi, meta) = total
      asIpmi(k) match {
        case None => asAssetMeta(k) match {
          case None => throw new Exception("%s is not an asset meta field or IPMI".format(k))
          case Some(assetMeta) =>
            ipmi -> ((assetMeta -> v) +: meta)
        }
        case Some(ipmiInfo) =>
          ((ipmiInfo -> v) +: ipmi) -> meta
      }
    }
  }

}
