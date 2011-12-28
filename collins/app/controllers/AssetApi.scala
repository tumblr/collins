package controllers

import models.{Status => AStatus}
import models._
import util._
import views.html

import play.api.data._
import play.api.http.{Status => StatusValues}
import play.api.mvc._
import play.api.libs.json._

import java.util.Date

trait AssetApi {
  this: Api with SecureController =>

  private lazy val lshwConfig = Helpers.subAsMap("lshw")

  def getAsset(tag: String) = Authenticated { user => Action { implicit req =>
    type IpmiMap = Map[String,JsValue]
    type Carry = Tuple4[Asset, LshwRepresentation, Seq[MetaWrapper], IpmiMap]

    val isHtml = OutputType.isHtml(req)
    withAssetFromTag(tag) { asset =>
      val (lshwRep, mvs) = LshwHelper.reconstruct(asset)
      val ipmi = IpmiInfo.findByAsset(asset).map { info =>
        hasRole(user.get, Seq("infra")) match {
          case true => info.toJsonMap(false)
          case _ => info.toJsonMap()
        }
      }.getOrElse(Map[String,JsValue]())
      val outMap = Map(
        "ASSET" -> JsObject(asset.toJsonMap),
        "HARDWARE" -> JsObject(lshwRep.toJsonMap),
        "IPMI" -> JsObject(ipmi),
        "ATTRIBS" -> JsObject(mvs.groupBy { _.getGroupId }.map { case(groupId, mv) =>
          groupId.toString -> JsObject(mv.map { mvw => mvw.getName -> JsString(mvw.getValue) }.toMap)
        }.toMap)
      )
      val extras: Carry = (asset, lshwRep, mvs, ipmi)
      ResponseData(Results.Ok, JsObject(outMap), attachment = Some(extras))
    }.map { data =>
      isHtml match {
        case true => data.status match {
          case Results.Ok =>
            val (asset, lshwRep, mv, ipmi) = data.attachment.get.asInstanceOf[Carry]
            Results.Ok(html.asset.show(asset, lshwRep, mv, ipmi))
          case _ =>
            Redirect(app.routes.Resources.index).flashing(
              "message" -> ("Could not find asset with tag " + tag)
            )
        }
        case _ => formatResponseData(data)
      }
    }
  }}(SecuritySpec(true, Nil))

  // GET /api/assets?params
  private val finder = new AssetApi.FindAsset()
  def getAssets(page: Int, size: Int, sort: String) = SecureAction { implicit req =>
    finder(page, size, sort)
  }(SecuritySpec(true, Nil))

  // PUT /api/asset/:tag
  private val assetCreator = new AssetApi.CreateAsset()
  def createAsset(tag: String) = SecureAction { implicit req =>
    formatResponseData(assetCreator(tag))
  }(SecuritySpec(true, Seq("infra")))

  // POST /api/asset/:tag
  val updateAsset = new UpdateAsset()

  // DELETE /api/asset/:tag
  def deleteAsset(tag: String) = SecureAction { implicit req =>
    import com.twitter.util.StateMachine.InvalidStateTransition
    val responseData = withAssetFromTag(tag) { asset =>
      Model.withTransaction { implicit con =>
        try {
          AssetStateMachine(asset).decommission().executeUpdate()
          AssetLog.create(AssetLog.informational(
            asset,
            "Asset decommissioned successfully",
            AssetLog.Formats.PlainText,
            AssetLog.Sources.Internal
          ))
          ResponseData(Results.Ok, JsObject(Map("SUCCESS" -> JsBoolean(true))))
        } catch {
          case e: InvalidStateTransition =>
            val msg = "Only assets in a cancelled state can be decommissioned"
            getErrorMessage(msg, Results.Status(StatusValues.CONFLICT))
          case e =>
            val msg = "Error saving response: %s".format(e.getMessage)
            getErrorMessage(msg, Results.InternalServerError)
        }
      }
    }
    formatResponseData(responseData)
  }(SecuritySpec(true, Seq("infra")))

  private[AssetApi] class UpdateAsset(perms: Seq[String] = Seq("infra")) {
    import play.api.libs.Files
    val updateForm = Form(of(
      "lshw" -> optional(text(1)),
      "lldp" -> optional(text(1)),
      "chassis_tag" -> optional(text(1)),
      "attribute" -> optional(text(3)).verifying("Invalid attribute specified", res => res match {
        case None => true
        case Some(v) => v.split(";", 2).size == 2
      })
    ))

    protected def getFormFile(key: String)(implicit req: Request[AnyContent]): Map[String,String] = {
      req.body match {
        case AnyContentAsMultipartFormData(mdf) => mdf.file(key) match {
          case Some(temporaryFile) =>
            val src = io.Source.fromFile(temporaryFile.ref.file)
            val txt = src.mkString
            src.close()
            Map(key -> txt)
          case None => Map.empty
        }
        case _ => Map.empty
      }
    }

    def validateRequest(asset: Asset)(implicit req: Request[AnyContent]): Either[String,Map[String,String]] = {
      updateForm.bindFromRequest.fold(
        hasErrors => Left("Error processing form data"),
        success => {
          val (lshw, lldp, chassis_tag, attribute) = success
          val map: Map[String,String] = Map.empty ++
            lshw.map { s => Map("lshw" -> s) }.getOrElse(getFormFile("lshw")) ++
            lldp.map { s => Map("lldp" -> s) }.getOrElse(getFormFile("lldp")) ++
            chassis_tag.map { s => Map("chassis_tag" -> s) }.getOrElse(Map.empty) ++
            attribute.map { attrib =>
              val attribs = attrib.split(";", 2)
              Map(attribs(0) -> attribs(1))
            }.getOrElse(Map.empty)
          Right(map)
        }
      )
    }

    def apply(tag: String) = SecureAction { implicit req =>
      val responseData = withAssetFromTag(tag) { asset =>
        validateRequest(asset) match {
          case Left(error) => getErrorMessage(error)
          case Right(options) =>
            AssetLifecycle.updateAsset(asset, options) match {
              case Left(error) => getErrorMessage(error.getMessage)
              case Right(success) =>
                ResponseData(Results.Ok, JsObject(Map("SUCCESS" -> JsBoolean(success))))
            }
        }
      }
      formatResponseData(responseData)
    }(SecuritySpec(true, Seq("infra")))
  }


}
object AssetApi extends ApiResponse {
  private[controllers] object FindAsset {
    val params = Set("attribute", "type", "status", "createdAfter", "createdBefore", "updatedAfter",
      "updatedBefore")
    val findForm = Form(of(
      "attribute" -> optional(text(3)).verifying("Invalid attribute specified", res => res match {
        case None => true
        case Some(s) => s.split(";", 2).size == 2
      }),
      "type" -> optional(text(2)).verifying("Invalid asset type specified", res => res match {
        case None => true
        case Some(s) => AssetType.Enum.values.find(_.toString == s).isDefined
      }),
      "status" -> optional(text(2)).verifying("Invalid asset status specified", res => res match {
        case None => true
        case Some(s) => AStatus.Enum.values.find(_.toString == s).isDefined
      }),
      "createdAfter" -> optional(date(Helpers.ISO_8601_FORMAT)),
      "createdBefore" -> optional(date(Helpers.ISO_8601_FORMAT)),
      "updatedAfter" -> optional(date(Helpers.ISO_8601_FORMAT)),
      "updatedBefore" -> optional(date(Helpers.ISO_8601_FORMAT))
    ))
  }

  private[controllers] object CreateAsset {
    val params = Set("generate_ipmi", "asset_type", "status")
    val createForm = Form(of(
      "generate_ipmi" -> optional(boolean),
      "asset_type" -> optional(text(1)).verifying("Invalid asset type specified", res => res match {
        case Some(asset_type) => AssetType.fromString(asset_type) match {
          case Some(_) => true
          case None => false
        }
        case None => true
      }),
      "status" -> optional(text(1)).verifying("Invalid asset status specified", res => res match {
        case Some(status) => AStatus.Enum.values.find(_.toString == status).isDefined
        case None => true
      })
    ))
  }

  private[controllers] class CreateAsset() {
    val defaultAssetType = AssetType.Enum.ServerNode

    type Success = (Option[Boolean],AssetType,Option[AStatus.Enum])
    def validateRequest()(implicit request: Request[AnyContent]): Either[String,Success] = {
      CreateAsset.createForm.bindFromRequest.fold(
        err => {
          val msg = err("generate_ipmi").error.map { _ =>
            "generate_ipmi only takes true or false as values"
          }.getOrElse(
            err("asset_type").error.map { _ =>
              "Invalid asset_type specified"
            }.getOrElse(
              err("status").error.map { _ =>
                "Invalid status specified"
              }.getOrElse("Error during parameter validation")
            )
          )
          Left(msg)
        },
        success => {
          val gen_ipmi = success._1
          val atype = success._2.flatMap { name =>
            AssetType.fromString(name)
          }.getOrElse(AssetType.fromEnum(defaultAssetType))
          val status = success._3.map { st =>
            AStatus.Enum.withName(st)
          }
          Right(gen_ipmi, atype, status)
        }
      )
    }

    def validateTag(tag: String): Option[ResponseData] = {
      Asset.isValidTag(tag) match {
        case false => Some(getErrorMessage("Invalid tag specified"))
        case true => Asset.findByTag(tag) match {
          case Some(asset) =>
            val msg = "Asset with tag '%s' already exists".format(tag)
            Some(getErrorMessage(msg, Results.Status(StatusValues.CONFLICT)))
          case None => None
        }
      }
    }

    protected def getCreateMessage(asset: Asset, ipmi: Option[IpmiInfo]): JsObject = {
      val map = ipmi.map { ipmi_info =>
          Map("ASSET" -> JsObject(asset.toJsonMap),
              "IPMI" -> JsObject(ipmi_info.toJsonMap(false)))
      }.getOrElse(Map("ASSET" -> JsObject(asset.toJsonMap)))
      JsObject(map)
    }

    def apply(tag: String)(implicit req: Request[AnyContent]) = {
      validateTag(tag) match {
        case Some(data) => data
        case None => validateRequest() match {
          case Left(error) => getErrorMessage(error)
          case Right((optGenerateIpmi, assetType, status)) =>
            val generateIpmi = optGenerateIpmi.getOrElse({
              assetType.getId == AssetType.Enum.ServerNode.id
            })
            AssetLifecycle.createAsset(tag, assetType, generateIpmi, status) match {
              case Left(ex) => getErrorMessage(ex.getMessage)
              case Right((asset, ipmi)) =>
                ResponseData(Results.Created, getCreateMessage(asset, ipmi))
            }
        }
      }
    }
  }

  private[controllers] class FindAsset() {

    def formatFormErrors(errors: Seq[FormError]): String = {
      errors.map { e =>
        e.key match {
          case "attribute" | "type" | "status" => "%s - %s".format(e.key, e.message)
          case key if key.startsWith("created") => "%s must be an ISO8601 date".format(key)
          case key if key.startsWith("updated") => "%s must be an ISO8601 date".format(key)
        }
      }.mkString(", ")
    }

    type Validated = (AttributeResolver.ResultTuple,AssetFinder)
    def validateRequest()(implicit req: Request[AnyContent]): Either[String,Validated] = {
      FindAsset.findForm.bindFromRequest.fold(
        errorForm => Left(formatFormErrors(errorForm.errors)),
        success => {
          val (attribute,atype,status,createdA,createdB,updatedA,updatedB) = success
          val attributeMap = attribute.map { _ =>
            req.queryString("attribute").foldLeft(Map[String,String]()) { case(total,cur) =>
              val split = cur.split(";", 2)
              if (split.size == 2) {
                total ++ Map(split(0) -> split(1))
              } else {
                return Left("attribute found but not formatted as key;value")
              }
            }
          }.getOrElse(Map[String,String]())
          val atypeEnum: Option[AssetType.Enum] = atype.map { at => AssetType.Enum.withName(at) }
          val statusEnum: Option[AStatus.Enum] = status.map { s => AStatus.Enum.withName(s) }
          val resolvedMap = try {
            AttributeResolver(attributeMap)
          } catch {
            case e => return Left(e.getMessage)
          }
          Right((resolvedMap,AssetFinder(statusEnum, atypeEnum, createdA, createdB, updatedA, updatedB)))
        }
      )
    }

    protected def formatAsJson(results: Page[Asset]): ResponseData = {
      ResponseData(Results.Ok, JsObject(results.getPaginationJsMap() ++ Map(
        "Data" -> JsArray(results.items.map { i => JsObject(i.toJsonMap) }.toList)
      )), results.getPaginationHeaders)
    }

    def apply(page: Int, size: Int, sort: String)(implicit req: Request[AnyContent]) = {
      val isHtml = OutputType.isHtml(req)
      validateRequest() match {
        case Left(err) => isHtml match {
          case true =>
            Redirect(app.routes.Resources.index).flashing(
              "error" -> ("Error executing search: " + err)
            )
          case false =>
            formatResponseData(getErrorMessage(err))
        }
        case Right(valid) =>
          val pageParams = PageParams(page, size, sort)
          val results = MetaWrapper.findAssets(pageParams, valid._1, valid._2)
          isHtml match {
            case false => formatResponseData(formatAsJson(results))
            case true => results.items.size match {
              case 0 => Redirect(app.routes.Resources.index).flashing(
                "message" -> ("Could not find any matching assets")
              )
              case n => Results.Ok(html.asset.list(results))
            }
          }
      }
    }
  }
}
