package controllers

import models.{Status => AStatus}
import models._
import util.{AssetStateMachine, Helpers, LshwParser, JsonOutput, SecuritySpec}

import play.api.data._
import play.api.mvc._
import play.api.json._

// FIXME: Add generate_ipmi flag to asset PUT endpoint
// FIXME: Don't require lshw if asset is not a server
trait AssetApi {
  this: Api with SecureController =>

  private lazy val lshwConfig = Helpers.subAsMap("lshw")

  // GET /api/asset/:tag/log
  def getLogData(tag: String, page: Int, size: Int) = SecureAction { implicit req =>
    val responseData = withAssetFromTag(tag) { asset =>
      val logs = AssetLog.list(asset, page, size)
      val logMessage: AssetLog => JsValue = { log =>
        log.is_json match {
          case true => parseJson(log.message)
          case false => JsString(log.message)
        }
      }
      val prevPage = logs.prev match {
        case None => 0
        case Some(n) => n
      }
      val nextPage = logs.next match {
        case None => page
        case Some(n) => n
      }
      val totalResults = logs.total
      val headers = Seq(
        ("X-Pagination-PreviousPage" -> prevPage.toString),
        ("X-Pagination-CurrentPage" -> page.toString),
        ("X-Pagination-NextPage" -> nextPage.toString),
        ("X-Pagination-TotalResults" -> totalResults.toString)
      )
      ResponseData(Ok, JsObject(Map(
        "Pagination" -> JsObject(Map(
          "PreviousPage" -> JsNumber(prevPage),
          "CurrentPage" -> JsNumber(page),
          "NextPage" -> JsNumber(nextPage),
          "TotalResults" -> JsNumber(logs.total)
        )),
        "Data" -> JsArray(logs.items.map { log =>
          JsObject(Map(
            "AssetTag" -> JsString(tag),
            "Created" -> JsString(Helpers.dateFormat(log.created)),
            "IsError" -> JsBoolean(log.is_error),
            "Message" -> logMessage(log)
          ))
        }.toList)
      )), headers)
    }
    formatResponseData(responseData)
  }

  // PUT /api/asset/:tag/log
  def submitLogData(tag: String) = SecureAction { implicit req =>

    def processJson(jsValue: JsValue, asset: Asset): Option[String] = {
      val is_error: Boolean = jsValue \ "IsError" match {
        case JsBoolean(bool) => bool
        case JsUndefined(msg) => false
        case _ => return Some("IsError must be a boolean")
      }
      val msg = jsValue \ "Message" match {
        case JsUndefined(msg) => return Some("Didn't find Message in json object")
        case js => js
      }
      Model.withConnection { implicit con =>
        AssetLog.create(AssetLog(asset, msg, is_error))
      }
      None
    }

    def processForm(asset: Asset): Option[String] = {
      Form(of(
            "message" -> requiredText(1),
            "is_error" -> optional(boolean)
           )
      ).bindFromRequest.fold(
        error => {
          val msg = error.errors.map { _.message }.mkString(", ")
          Some(msg)
        },
        success => {
          val msg = success._1
          val is_error = success._2.getOrElse(false)
          Model.withConnection { implicit con =>
            AssetLog.create(AssetLog(asset, msg, is_error))
          }
          None
        }
      )
    }

    val responseData = withAssetFromTag(tag) { asset =>
      (req.body.asJson match {
        case Some(jsValue) => processJson(jsValue, asset)
        case None => processForm(asset)
      }).map { err =>
        getErrorMessage(err)
      }.getOrElse {
        ResponseData(Created, JsObject(Map("Success" -> JsBoolean(true))))
      }
    }
    formatResponseData(responseData)
  }(SecuritySpec(isSecure = true, Seq("infra")))

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

    def doCreateAsset(tag: String): ResponseData = {
      import IpmiInfo.Enum._
      try {
        Model.withTransaction { implicit con =>
          val asset = Asset.create(Asset(tag, AStatus.Enum.Incomplete, AssetType.Enum.ServerNode))
          val ipmi = IpmiInfo(asset)
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
          ResponseData(Ok, getCreateMessage(asset, IpmiInfo(asset)))
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

  private def withAssetFromTag(tag: String)(f: Asset => ResponseData): ResponseData = {
    Asset.isValidTag(tag) match {
      case false => getErrorMessage("Empty tag specified")
      case true => Asset.findByTag(tag) match {
        case Some(asset) => f(asset)
        case None => getErrorMessage("Could not find specified asset", NotFound)
      }
    }
  }

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

  private def getErrorMessage(msg: String, status: Status = BadRequest) = {
    ResponseData(status, JsObject(Map("ERROR_DETAILS" -> JsString(msg))))
  }

  private def getCreateMessage(asset: Asset, ipmi: IpmiInfo): JsObject = {
    val assetAsMap = assetToJsMap(asset)
    val ipmiAsMap = ipmi.toMap().map { case(k,v) =>
      k -> JsString(v)
    }
    JsObject(assetAsMap ++ Map("IPMI" -> JsObject(ipmiAsMap)))
  }

  private def assetToJsMap(asset: Asset) = Map("ASSET" -> JsObject(asset.toMap().map { case(k,v) =>
    k -> JsString(v)
  }))

}
