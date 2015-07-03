package collins.models

import java.util.Date

import scala.util.control.Exception.allCatch

import play.api.Logger

import collins.models.conversions._
import collins.models.AssetMeta.Enum.RackPosition
import collins.models.{Status => AStatus}
import collins.models.logs._

import collins.util.AssetStateMachine
import collins.util.Tattler
import collins.util.LldpRepresentation
import collins.util.LshwRepresentation
import collins.util.config.Feature
import collins.util.config.LshwConfig
import collins.util.parsers.LldpParser
import collins.util.parsers.LshwParser
import collins.solr.Solr
import collins.util.power.PowerUnits

object AssetLifecycleConfig {
  // Don't want people trying to set status/tag/etc via attribute
  private val PossibleAssetKeys = Set("STATUS", "STATE", "TAG", "TYPE", "IP_ADDRESS")
  // A few keys we generally want changable after intake
  private val ExcludedKeys = Set(AssetMeta.Enum.ChassisTag.toString)
  // User configured excludes, only applied to non-servers
  private def configuredExcludes = Feature.allowTagUpdates
  private val RestrictedKeys = AssetMeta.Enum.values.map { _.toString }.toSet ++ PossibleAssetKeys -- ExcludedKeys

  def isRestricted(s: String) = {
    if (Feature.sloppyTags) {
      false
    } else {
      RestrictedKeys.contains(s.toUpperCase)
    }
  }

  def withExcludes(includeUser: Boolean = false) = includeUser match {
    case false => RestrictedKeys
    case true => RestrictedKeys -- configuredExcludes
  }
}

object AssetLifecycle {
  type AssetIpmi = Tuple2[Asset,Option[IpmiInfo]]
  type Status[T] = Either[Throwable,T]  
}
// Supports meta operations on assets
class AssetLifecycle(user: Option[User], tattler: Tattler) {

  private[this] val logger = Logger.logger

  def createAsset(tag: String, assetType: AssetType, generateIpmi: Boolean, status: Option[AStatus]): AssetLifecycle.Status[AssetLifecycle.AssetIpmi] = {
    import IpmiInfo.Enum._
    try {
      val _status = status.getOrElse(Status.Incomplete.get)
      val res = Asset.inTransaction {
        val asset = Asset.create(Asset(tag, _status, assetType))
        val ipmi = generateIpmi match {
          case true => Some(IpmiInfo.createForAsset(asset))
          case false => None
        }
        Solr.updateAsset(asset)
        Tuple2(asset, ipmi)
      }
      tattler.informational(
        "Initial intake successful, status now %s".format(_status.toString), res._1)
      Right(res)
    } catch {
      case e: Throwable =>
        tattler.system("Failed to create asset %s: %s".format(tag, e.getMessage))
        Left(e)
    }
  }

  def decommissionAsset(asset: Asset, options: Map[String,String]): AssetLifecycle.Status[Boolean] = {
    val reason = options.get("reason").map { r =>
      r + " : status is %s".format(asset.getStatusName)
    }.getOrElse(
      "Decommission of asset requested, status is %s".format(asset.getStatusName)
    )
    try {
      Asset.inTransaction {
        tattler.informational(reason, asset)
        AssetStateMachine(asset).decommission()
        tattler.informational("Asset decommissioned successfully", asset)
      }
      Right(true)
    } catch {
      case e: Throwable => Left(e)
    }
  }

  def updateAsset(asset: Asset, options: Map[String,String]): AssetLifecycle.Status[Boolean] = asset.isServerNode match {
    case true  => updateServer(asset, options)
    case false => updateAssetAttributes(asset, options)
  }

  protected def updateServer(asset: Asset, options: Map[String,String]): AssetLifecycle.Status[Boolean] = {
    if (asset.isIncomplete) {
      updateIncompleteServer(asset, options)
    } else if (asset.isNew) {
      updateNewServer(asset, options)
    } else {
      updateServerHardwareMeta(asset, options)
    }
  }

  def updateAssetAttributes(asset: Asset, options: Map[String,String]): AssetLifecycle.Status[Boolean] = {
    asset.isServerNode match {
      case true => updateAssetAttributes(asset, options, AssetLifecycleConfig.withExcludes(false))
      case false => updateAssetAttributes(asset, options, AssetLifecycleConfig.withExcludes(true))
    }
  }

  protected def updateAssetAttributes(asset: Asset, options: Map[String,String], restricted: Set[String]): AssetLifecycle.Status[Boolean] = {
    allCatch[Boolean].either {
      val groupId = options.get("groupId").map(_.toInt)
      val state = options.get("state").flatMap(s => State.findByName(s))
      val status = options.get("status").flatMap(s => AStatus.findByName(s)).map(_.id)
      val opts = options - "state" - "groupId" - "status"
      if (!asset.isConfiguration) {
        opts.find(kv => restricted(kv._1)).map(kv =>
          return Left(new Exception("Attribute %s is restricted".format(kv._1)))
        )
      }
      Asset.inTransaction {
        MetaWrapper.createMeta(asset, opts, groupId)
        Asset.partialUpdate(asset, Some(new Date().asTimestamp), status, state)
        true
      }
    }.left.map(e => handleException(asset, "Error saving attributes for asset", e))
  }

  def updateAssetStatus(asset: Asset, status: Option[AStatus], state: Option[State], reason: String): AssetLifecycle.Status[Boolean] = {
    if (!Feature.sloppyStatus) {
      return Left(new Exception("features.sloppyStatus is not enabled"))
    }
    allCatch[Boolean].either {
      val oldStatus = asset.getStatusName
      val oldState = asset.getStateName
      Asset.partialUpdate(asset, Some(new Date().asTimestamp), status.map(_.id), state)
      val newStatus = status.map(_.name).getOrElse("NotUpdated")
      val newState = state.map(_.name).getOrElse("NotUpdated")
      val message = "Old status:state (%s:%s) -> New status:state (%s:%s) - %s".format(
        oldStatus, oldState, newStatus, newState, reason
      )
      tattler.informational(message, asset)
      true
    }.left.map(e => handleException(asset, "Error updating status/state for asset", e))
  }

  protected def updateServerHardwareMeta(asset: Asset, options: Map[String,String]): AssetLifecycle.Status[Boolean] = {
    // if asset's status is in the allowed statuses for updating, do it
    if (Feature.allowedServerUpdateStatuses.contains(asset.getStatus())) {
      // we will allow updates to lshw/lldp while the machine is in these statuses
      allCatch[Boolean].either {
        Asset.inTransaction {
          options.get("lshw").foreach{lshw =>
            parseLshw(asset, new LshwParser(lshw)).left.foreach{throw _}
            tattler.informational("Parsing and storing LSHW data succeeded", asset)
          }
          options.get("lldp").foreach{lldp =>
            parseLldp(asset, new LldpParser(lldp)).left.foreach{throw _}
            tattler.informational("Parsing and storing LLDP data succeeded", asset)
          }
          options.get("CHASSIS_TAG").foreach{chassis_tag =>
            MetaWrapper.createMeta(asset, Map(AssetMeta.Enum.ChassisTag.toString -> chassis_tag))
          }
          true
        }
      }.left.map(e => handleException(asset, "Exception updating asset", e))
    } else {
      Left(new Exception("Only updates for servers in statuses " + Feature.allowedServerUpdateStatuses.mkString(", ") + " are currently supported"))
    }
  }

  protected def updateNewServer(asset: Asset, options: Map[String,String]): AssetLifecycle.Status[Boolean] = {
    val units = PowerUnits()
    val requiredKeys = Set(RackPosition.toString) ++ PowerUnits.keys(units)
    requiredKeys.find(key => !options.contains(key)).map { not_found =>
      return Left(new Exception(not_found + " parameter not specified"))
    }

    val rackpos = options(RackPosition.toString)

    val filtered = options.filter(kv => !requiredKeys(kv._1))
    filtered.find(kv => AssetLifecycleConfig.isRestricted(kv._1)).map(kv =>
      return Left(new Exception("Attribute %s is restricted".format(kv._1)))
    )

    allCatch[Boolean].either {
      val values = Seq(AssetMetaValue(asset, RackPosition, rackpos)) ++
                   PowerUnits.toMetaValues(units, asset, options)
      val unallocatedAsset = Asset.inTransaction {
        AssetMetaValue.purge(values, Some(0))
        val created = AssetMetaValue.create(values)
        require(created == values.length,
          "Should have created %d rows, created %d".format(values.length, created))
        val newAsset = asset.copy(status = Status.Unallocated.map(_.id).getOrElse(0), updated = Some(new Date().asTimestamp))
        MetaWrapper.createMeta(newAsset, filtered)
        Asset.partialUpdate(newAsset, newAsset.updated, Some(newAsset.status), State.Starting)
        newAsset
      }
      tattler.informational("Intake now complete, asset Unallocated", unallocatedAsset)
      true
    }.left.map(e => handleException(asset, "Exception updating asset", e))
  }

  protected def updateIncompleteServer(asset: Asset, options: Map[String,String]): AssetLifecycle.Status[Boolean] = {
    val requiredKeys = Set("lshw", "lldp", "CHASSIS_TAG")
    requiredKeys.find(key => !options.contains(key)).map { not_found =>
      return Left(new Exception(not_found + " parameter not specified"))
    }

    val lshw = options("lshw")
    val lldp = options("lldp")
    val chassis_tag = options("CHASSIS_TAG")

    val filtered = options.filter(kv => !requiredKeys(kv._1))
    filtered.find(kv => AssetLifecycleConfig.isRestricted(kv._1)).map(kv =>
      return Left(new Exception("Attribute %s is restricted".format(kv._1)))
    )
    val lshwParser = new LshwParser(lshw)
    val lldpParser = new LldpParser(lldp)

    allCatch[Boolean].either {
      Asset.inTransaction {
        val lshwParsingResults = parseLshw(asset, lshwParser)
        if (lshwParsingResults.isLeft) {
          throw lshwParsingResults.left.get
        }
        val lldpParsingResults = parseLldp(asset, lldpParser)
        if (lldpParsingResults.isLeft) {
          throw lldpParsingResults.left.get
        }
        MetaWrapper.createMeta(asset, filtered ++ Map(AssetMeta.Enum.ChassisTag.toString -> chassis_tag))
        val newAsset = asset.copy(status = Status.New.map(_.id).getOrElse(0), updated = Some(new Date().asTimestamp))
        Asset.partialUpdate(newAsset, newAsset.updated, Some(newAsset.status), State.New)
        tattler.informational("Parsing and storing LSHW/LLDP data succeeded", newAsset)
        true
      }
    }.left.map(e => handleException(asset, "Exception updating asset", e))
  }

  protected def parseLshw(asset: Asset, parser: LshwParser): AssetLifecycle.Status[LshwRepresentation] = {
    parser.parse() match {
      case Left(ex) =>
        AssetLog.notice(asset, user.map{ _.username }.getOrElse(""), "Parsing LSHW failed", LogFormat.PlainText,
          LogSource.Internal).withException(ex).create()
        Left(ex)
      case Right(lshwRep) =>
        LshwHelper.updateAsset(asset, lshwRep) match {
          case true =>
            Right(lshwRep)
          case false =>
            val ex = new Exception("Parsing LSHW succeeded but saving failed")
            Left(handleException(asset, ex.getMessage, ex))
        }
    } //catch
  } // updateServer

  protected def parseLldp(asset: Asset, parser: LldpParser): AssetLifecycle.Status[LldpRepresentation] = {
    parser.parse() match {
      case Left(ex) =>
        AssetLog.notice(asset, user.map{ _.username }.getOrElse(""), "Parsing LLDP failed", LogFormat.PlainText,
          LogSource.Internal).withException(ex).create()
        Left(ex)
      case Right(lldpRep) =>
        LldpHelper.updateAsset(asset, lldpRep) match {
          case true =>
            Right(lldpRep)
          case false =>
            val ex = new Exception("Parsing LLDP succeeded but saving failed")
            Left(handleException(asset, ex.getMessage, ex))
        }
    }
  }

  private def handleException(asset: Asset, msg: String, e: Throwable): Throwable = {
    logger.warn(msg, e)
    try {
      AssetLog.error(
        asset,
        user.map{ _.username }.getOrElse(""), 
        msg,
        LogFormat.PlainText,
        LogSource.Internal
      ).withException(e).create()
    } catch {
      case ex: Throwable =>
        logger.error("Database problems", ex)
        tattler.system("Database problems: %s".format(ex.getMessage))
    }
    e
  }
}
