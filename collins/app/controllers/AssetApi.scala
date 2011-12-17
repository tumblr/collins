package controllers

import models.{Status => AStatus}
import models._
import util.{AssetStateMachine, Helpers, LshwParser, JsonOutput, SecuritySpec}

import play.api.data._
import play.api.mvc._
import play.api.json._

trait AssetApi {
  this: Api with SecureController =>

  private lazy val lshwConfig = Helpers.subAsMap("lshw")

  // GET /api/asset/:tag
  def findAssetWithMetaValues(tag: String) = SecureAction { implicit req =>
    val responseData = withAssetFromTag(tag) { asset =>
      val assetAsMap = assetToJsMap(asset)
      val assetMeta = AssetMetaValue.findAllByAssetId(asset.getId).groupBy { _.getGroupId }
      val attribsObj = JsObject(assetMeta.map { case(groupId, mvl) =>
        groupId.toString -> JsObject(mvl.map { mv => mv.getName -> JsString(mv.getValue) }.toMap)
      })
      val attribMap = Map("ATTRIBS" -> attribsObj)
      ResponseData(Ok, JsObject(assetAsMap ++ attribMap))
    }
    formatResponseData(responseData)
  }

  // PUT /api/asset/:tag
  def createAsset(tag: String) = SecureAction { implicit req =>

    def handleValidation(): Either[String,(Option[Boolean],AssetType)] = {
      val defaultAssetType = AssetType.Enum.ServerNode
      val form = Form(of(
        "generate_ipmi" -> optional(boolean),
        "asset_type" -> optional(text(1)).verifying("Invalid asset type specified", res => res match {
          case Some(asset_type) => AssetType.fromString(asset_type) match {
            case Some(_) => true
            case None => false
          }
          case None => true
        })
      ))
      form.bindFromRequest.fold(
        err => {
          val msg = err("generate_ipmi").error.map { _ =>
            "generate_ipmi only takes true or false as values"
          }.getOrElse(
            err("asset_type").error.map { _ =>
              "Invalid asset_type specified"
            }.getOrElse("Error during parameter validation")
          )
          Left(msg)
        },
        success => {
          val gen_ipmi = success._1
          val atype = success._2.flatMap { name =>
            AssetType.fromString(name)
          }.getOrElse(AssetType.fromEnum(defaultAssetType))
          Right(gen_ipmi, atype)
        }
      )
    }

    val responseData = Asset.isValidTag(tag) match {
      case false => getErrorMessage("Invalid tag specified")
      case true => Asset.findByTag(tag) match {
        case Some(asset) => Model.withConnection { implicit con =>
          ResponseData(Ok, getCreateMessage(asset, IpmiInfo.findByAsset(asset)))
        }
        case None => handleValidation() match {
          case Left(error) => getErrorMessage(error)
          case Right((optGenerateIpmi, assetType)) =>
            val generateIpmi = optGenerateIpmi.getOrElse({
              assetType.getId == AssetType.Enum.ServerNode.id
            })
            AssetLifecycle.createAsset(tag, assetType, generateIpmi) match {
              case Left(ex) => getErrorMessage(ex.getMessage)
              case Right((asset, ipmi)) =>
                ResponseData(Created, getCreateMessage(asset, ipmi))
            }
        }
      }
    }
    formatResponseData(responseData)
  }(SecuritySpec(true, Seq("infra")))

  // POST /api/asset/:tag
  def updateAsset(tag: String) = SecureAction { implicit req =>

    def handleValidation(asset: Asset): Either[String,(Option[String],Option[String])] = {
      val form = Form(of(
        "lshw" -> optional(text(1)),
        "lldpd" -> optional(text(1))
      ))
      form.bindFromRequest.fold(
        err => Left("Error processing form data"),
        success => Right(success._1, success._2)
      )
    }

    val responseData = withAssetFromTag(tag) { asset =>
      if (asset.status != AStatus.Enum.Incomplete.id) {
        getErrorMessage("Asset update only works when asset is Incomplete")
      } else {
        handleValidation(asset) match {
          case Left(error) => getErrorMessage(error)
          case Right((lshw,lldp)) =>
            AssetLifecycle.updateAsset(asset, lshw, lldp) match {
              case Left(error) => getErrorMessage(error.getMessage)
              case Right(success) =>
                ResponseData(Ok, JsObject(Map("SUCCESS" -> JsBoolean(success))))
            }
        }
      }
    }
    formatResponseData(responseData)
  }(SecuritySpec(true, Seq("infra")))

  // DELETE /api/asset/:tag
  def deleteAsset(tag: String) = SecureAction { implicit req =>
    import com.twitter.util.StateMachine.InvalidStateTransition
    import play.api.http.{Status => StatusValues}
    val responseData = withAssetFromTag(tag) { asset =>
      Model.withTransaction { implicit con =>
        try {
          AssetStateMachine(asset).decommission().executeUpdate()
          AssetLog.create(AssetLog(
            asset,
            "Asset decommissioned successfully",
            false
          ))
          ResponseData(Ok, JsObject(Map("SUCCESS" -> JsBoolean(true))))
        } catch {
          case e: InvalidStateTransition =>
            val msg = "Only assets in a cancelled state can be decommissioned"
            getErrorMessage(msg, Status(StatusValues.CONFLICT))
          case e =>
            val msg = "Error saving response: %s".format(e.getMessage)
            getErrorMessage(msg, InternalServerError)
        }
      }
    }
    formatResponseData(responseData)
  }(SecuritySpec(true, Seq("infra")))


  private def getCreateMessage(asset: Asset, ipmi: Option[IpmiInfo]): JsObject = {
    val assetAsMap = assetToJsMap(asset)
    val map = ipmi.isDefined match {
      case true =>
        val ipmiAsMap = ipmi.get.toMap().map { case(k,v) => k -> JsString(v) }
        assetAsMap ++ Map("IPMI" -> JsObject(ipmiAsMap))
      case false =>
        assetAsMap
    }
    JsObject(map)
  }

  private def assetToJsMap(asset: Asset) = Map("ASSET" -> JsObject(asset.toMap().map { case(k,v) =>
    k -> JsString(v)
  }))

}
