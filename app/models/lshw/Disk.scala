package models.lshw

import util.ByteStorageUnit
import play.api.libs.json._
import Json.toJson

object Disk {

  type Type = Type.Value
  object Type extends Enumeration {
    val Ide = Value("IDE")
    val Scsi = Value("SCSI")
    val Flash = Value("PCIe")
    val CdRom = Value("CD-ROM")
  }

  implicit object DiskFormat extends Format[Disk] {
    override def reads(json: JsValue) = JsSuccess(Disk(
      ByteStorageUnit((json \ "SIZE").as[Long]),
      Disk.Type.withName((json \ "TYPE").as[String]),
      (json \ "DESCRIPTION").as[String],
      (json \ "PRODUCT").as[String],
      (json \ "VENDOR").as[String]
    ))
    override def writes(disk: Disk) = JsObject(Seq(
      "SIZE" -> toJson(disk.size.inBytes),
      "SIZE_S" -> toJson(disk.size.inBytes.toString),
      "SIZE_HUMAN" -> toJson(disk.size.toHuman),
      "TYPE" -> toJson(disk.diskType.toString),
      "DESCRIPTION" -> toJson(disk.description),
      "PRODUCT" -> toJson(disk.product),
      "VENDOR" -> toJson(disk.vendor)
    ))
  }
}

case class Disk(
  size: ByteStorageUnit, diskType: Disk.Type, description: String, product: String, vendor: String
) extends LshwAsset {

  import Disk._

  def isIde(): Boolean = diskType == Type.Ide
  def isScsi(): Boolean = diskType == Type.Scsi
  def isFlash(): Boolean = diskType == Type.Flash
  def isCdRom(): Boolean = diskType == Type.CdRom

  override def toJsValue() = toJson(this)
}
