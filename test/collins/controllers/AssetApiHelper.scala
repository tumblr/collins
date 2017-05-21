package collins.controllers

import org.specs2.matcher.JsonMatchers

import play.api.libs.Files.TemporaryFile
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.mvc.AnyContentAsMultipartFormData
import play.api.mvc.MultipartFormData
import play.api.mvc.MultipartFormData.FilePart

import collins.FakeRequest
import collins.ResourceFinder
import collins.ResponseMatchHelpers
import collins.models.AssetMeta.Enum.RackPosition
import collins.models.Status
import collins.util.power.PowerUnits

trait AssetApiHelper extends ResponseMatchHelpers with JsonMatchers with ControllerSpec with ResourceFinder {
  val assetTag: String
  def assetUrl = "/api/asset/%s.json".format(assetTag)
  val findUrl = "/api/assets.json"
  val user = getLoggedInUser("infra")
  val api = getApi(user)

  def waitOnCallbacks() {
    Thread.sleep(5000)
  }
  def createAsset() = {
    val createRequest = FakeRequest("PUT", assetUrl)
    val createResult = Extract.from(api.createAsset(assetTag).apply(createRequest))
    createResult
  }

  def updateAttributes() = {
    val rp: String = RackPosition.toString
    val units = PowerUnits()
    val unitSeq = (for (unit <- units; component <- unit) yield (component.key, Seq("%s %d".format(component.componentType.name, component.id)))).toSeq
    val powerMap = Map(unitSeq: _*)
    val requestBody = AnyContentAsFormUrlEncoded(Map(
      rp -> Seq("rack 1"),
      "attribute" -> Seq("foo;bar", "fizz;buzz")) ++ powerMap)
    val req = FakeRequest("POST", assetUrl, requestBody)
    val result = Extract.from(api.updateAsset(assetTag).apply(req))
    result
  }

  def updateStatus(status: Status, reason: String) = {
    val requestBody = AnyContentAsFormUrlEncoded(Map(
      "status" -> Seq(status.name),
      "reason" -> Seq(reason)))

    val req = FakeRequest("POST", assetUrl, requestBody)
    val result = Extract.from(api.updateAssetStatus(assetTag).apply(req))
    result
  }

  def updateHwInfo(lshwXml: String = "lshw-basic.xml") = {
    // update hw details (lshw / lldp) - cannot proceed without this
    val lshwData = getResource(lshwXml)
    val lldpData = getResource("lldpctl-two-nic.xml")
    val dummy = Seq[FilePart[TemporaryFile]]()
    val mdf = MultipartFormData(Map(
      "lshw" -> Seq(lshwData),
      "lldp" -> Seq(lldpData),
      "CHASSIS_TAG" -> Seq("abbacadabra")), dummy, Nil, Nil)
    val body = AnyContentAsMultipartFormData(mdf)
    val request = FakeRequest("POST", assetUrl, body)
    val result = Extract.from(api.updateAsset(assetTag).apply(request))
    result
  }

  def getAsset() = {
    val request = FakeRequest("GET", assetUrl)
    Extract.from(api.getAsset(assetTag).apply(request))
  }
}
