package controllers

import models.{Status => AStatus}
import models._
import util.{AssetStateMachine, Helpers, LshwParser, JsonOutput, SecuritySpec}

import play.api.data._
import play.api.mvc._
import play.api.json._

// FIXME: Don't require lshw if asset is not a server
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

    val defaultAssetType = AssetType.Enum.ServerNode

    val (generate_ipmi, asset_type) = Form(of(
      "generate_ipmi" -> optional(boolean),
      "asset_type" -> optional(text(1))
    )).bindFromRequest.fold(
      err => (false, AssetType.fromEnum(defaultAssetType)),
      success => {
        val gen_ipmi = success._1.getOrElse(true)
        val atype = success._2.map { name =>
          try {
            AssetType.fromEnum(AssetType.Enum.withName(name))
          } catch {
            case _ => AssetType.findByName(name).get
          }
        }.getOrElse(AssetType.fromEnum(defaultAssetType))
        (gen_ipmi, atype)
      }
    )

    def doCreateAsset(tag: String): ResponseData = {
      import IpmiInfo.Enum._
      try {
        Model.withTransaction { implicit con =>
          val asset = Asset.create(Asset(tag, AStatus.Enum.Incomplete, asset_type))
          val ipmi = generate_ipmi match {
            case true => Some(IpmiInfo.createForAsset(asset))
            case false => None
          }
          AssetLog.create(AssetLog(
            asset,
            "Initial intake successful, status now Incomplete",
            false
          ))
          ResponseData(Created, getCreateMessage(asset, ipmi))
        }
      } catch {
        case e => getErrorMessage(e.getMessage)
      }
    }

    val responseData = Asset.isValidTag(tag) match {
      case false => getErrorMessage("Invalid tag specified")
      case true => Asset.findByTag(tag) match {
        case Some(asset) => Model.withConnection { implicit con =>
          ResponseData(Ok, getCreateMessage(asset, IpmiInfo.findByAsset(asset)))
        }
        case None => doCreateAsset(tag)
      }
    }

    formatResponseData(responseData)
  }(SecuritySpec(true, Seq("infra")))

  // POST /api/asset/:tag
  def updateAsset(tag: String) = SecureAction { implicit req =>
    val responseData = withAssetFromTag(tag) { asset =>
      if (asset.status != AStatus.Enum.Incomplete.id) {
        getErrorMessage("Asset update only works when asset is Incomplete")
      } else {
        Form("lshw" -> requiredText).bindFromRequest.fold(
          noLshw => {
            val msg = noLshw.errors.map { _.message }.mkString(", ")
            Model.withConnection { implicit con =>
              AssetLog.create(AssetLog(asset, "Failed asset update: " + msg, true))
            }
            getErrorMessage(msg)
          },
          lshw => doUpdate(asset, lshw)
        )
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


  private def doUpdate(asset: Asset, lshw: String): ResponseData = {
    val parser = new LshwParser(lshw, lshwConfig)
    val parsed = parser.parse()
    parsed match {
      case Left(e) => {
        Model.withConnection { implicit con =>
          AssetLog.create(AssetLog(asset, "Parsing LSHW failed: " + e.getMessage, true))
        }
        getErrorMessage(e.getMessage)
      }
      case Right(lshwRep) => try {
        Model.withTransaction { implicit con =>
          LshwHelper.updateAsset(asset, lshwRep) match {
            case true =>
              AssetStateMachine(asset).update().executeUpdate()
              AssetLog.create(AssetLog(
                asset,
                "Parsing and storing LSHW data succeeded, asset now New",
                false
              ))
              ResponseData(Ok, JsObject(Map("SUCCESS" -> JsBoolean(true))))
            case false =>
              AssetLog.create(AssetLog(asset, "Parsing LSHW succeeded, saving failed", true))
              getErrorMessage("Error saving values", InternalServerError)
          }
        }
      } catch {
        case e: Throwable =>
          val msg = "Error saving values: %s".format(e.getMessage)
          getErrorMessage(msg, InternalServerError)
      }
    }
  }

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
