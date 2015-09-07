package collins.models.lshw

import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json

import collins.util.ByteStorageUnit

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
      (json \ "VENDOR").as[String]))
    override def writes(disk: Disk) = JsObject(Seq(
      "SIZE" -> Json.toJson(disk.size.inBytes),
      "SIZE_S" -> Json.toJson(disk.size.inBytes.toString),
      "SIZE_HUMAN" -> Json.toJson(disk.size.toHuman),
      "TYPE" -> Json.toJson(disk.diskType.toString),
      "DESCRIPTION" -> Json.toJson(disk.description),
      "PRODUCT" -> Json.toJson(disk.product),
      "VENDOR" -> Json.toJson(disk.vendor)))
  }
}

case class Disk(
    size: ByteStorageUnit, diskType: Disk.Type, description: String, product: String, vendor: String) extends LshwAsset {

  import Disk._

  def isIde(): Boolean = diskType == Type.Ide
  def isScsi(): Boolean = diskType == Type.Scsi
  def isFlash(): Boolean = diskType == Type.Flash
  def isCdRom(): Boolean = diskType == Type.CdRom

  override def toJsValue() = Json.toJson(this)
}
