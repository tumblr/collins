package models

import conversions._
import AssetMeta.Enum.RackPosition
import models.{Status => AStatus}
import models.logs._

import util.{ApiTattler, AssetStateMachine, InternalTattler, LldpRepresentation, LshwRepresentation, SystemTattler}
import util.config.{Feature, LshwConfig}
import util.parsers.{LldpParser, LshwParser}
import collins.solr.Solr
import util.power.PowerUnits

import play.api.Logger

import scala.util.control.Exception.allCatch
import java.util.Date

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
    case true => RestrictedKeys -- Feature.allowTagUpdates
  }
}

// Supports meta operations on assets
object AssetLifecycle {

  private[this] val logger = Logger.logger

  type AssetIpmi = Tuple2[Asset,Option[IpmiInfo]]
  type Status[T] = Either[Throwable,T]

  def createAsset(tag: String, assetType: AssetType, generateIpmi: Boolean, status: Option[AStatus]): Status[AssetIpmi] = {
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
      InternalTattler.informational(res._1, None,
        "Initial intake successful, status now %s".format(_status.toString))
      Asset.flushCache(res._1)
      Right(res)
    } catch {
      case e =>
        SystemTattler.safeError("Failed to create asset %s: %s".format(tag, e.getMessage))
        Left(e)
    }
  }

  def decommissionAsset(asset: Asset, options: Map[String,String]): Status[Boolean] = {
    val reason = options.get("reason").map { r =>
      r + " : status is %s".format(asset.getStatusName)
    }.getOrElse(
      "Decommission of asset requested, status is %s".format(asset.getStatusName)
    )
    try {
      Asset.inTransaction {
        InternalTattler.informational(asset, None, reason)
        AssetStateMachine(asset).decommission()
        InternalTattler.informational(asset, None, "Asset decommissioned successfully")
      }
      Right(true)
    } catch {
      case e => Left(e)
    }
  }

  def updateAsset(asset: Asset, options: Map[String,String]): Status[Boolean] = asset.isServerNode match {
    case true => updateServer(asset, options)
    case false => updateOther(asset, options)
  }

  protected def updateOther(asset: Asset, options: Map[String,String]): Status[Boolean] = {
    updateAssetAttributes(asset, options)
  }

  protected def updateServer(asset: Asset, options: Map[String,String]): Status[Boolean] = {
    if (asset.isIncomplete) {
      updateIncompleteServer(asset, options)
    } else if (asset.isNew) {
      updateNewServer(asset, options)
    } else if (asset.isMaintenance) {
      updateMaintenanceServer(asset, options)
    } else {
      Left(new Exception("Only updates for Incomplete, New, and Maintenance servers are currently supported"))
    }
  }

  def updateAssetAttributes(asset: Asset, options: Map[String,String]): Status[Boolean] = {
    asset.isServerNode match {
      case true => updateAssetAttributes(asset, options, AssetLifecycleConfig.withExcludes(false))
      case false => updateAssetAttributes(asset, options, AssetLifecycleConfig.withExcludes(true))
    }
  }

  protected def updateAssetAttributes(asset: Asset, options: Map[String,String], restricted: Set[String]): Status[Boolean] = {
    allCatch[Boolean].either {
      val groupId = options.get("groupId").map(_.toInt)
      val state = options.get("state").flatMap(s => State.findByName(s))
      val status = options.get("status").flatMap(s => AStatus.findByName(s)).map(_.id)
      val opts = options - "state" - "groupId" - "status"
      logger.debug(restricted.toString)
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

  def updateAssetStatus(asset: Asset, status: Option[AStatus], state: Option[State], reason: String): Status[Boolean] = {
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
      ApiTattler.informational(asset, None, message)
      true
    }.left.map(e => handleException(asset, "Error updating status/state for asset", e))
  }

  protected def updateNewServer(asset: Asset, options: Map[String,String]): Status[Boolean] = {
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
        val created = AssetMetaValue.create(values)
        require(created == values.length,
          "Should have created %d rows, created %d".format(values.length, created))
        val newAsset = asset.copy(status = Status.Unallocated.map(_.id).getOrElse(0), updated = Some(new Date().asTimestamp))
        MetaWrapper.createMeta(newAsset, filtered)
        Asset.partialUpdate(newAsset, newAsset.updated, Some(newAsset.status), State.Starting)
        newAsset
      }
      ApiTattler.informational(unallocatedAsset, None, "Intake now complete, asset Unallocated")
      true
    }.left.map(e => handleException(asset, "Exception updating asset", e))
  }

  protected def updateIncompleteServer(asset: Asset, options: Map[String,String]): Status[Boolean] = {
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
        InternalTattler.informational(newAsset, None, "Parsing and storing LSHW data succeeded")
        true
      }
    }.left.map(e => handleException(asset, "Exception updating asset", e))
  }

  
  protected def updateMaintenanceServer(asset: Asset, options: Map[String, String]): Status[Boolean] = {
    //only lshw,lldp can be updated in maintenance mode
    allCatch[Boolean].either {
      Asset.inTransaction {
        options.get("lshw").foreach{lshw => 
          parseLshw(asset, new LshwParser(lshw)).left.foreach{throw _}
        }
        options.get("lldp").foreach{lldp =>
          parseLldp(asset, new LldpParser(lldp)).left.foreach{throw _}
        }
        true
      }
    }.left.map(e => handleException(asset, "Exception updating asset", e))
  }

  protected def parseLshw(asset: Asset, parser: LshwParser): Status[LshwRepresentation] = {
    parser.parse() match {
      case Left(ex) =>
        AssetLog.notice(asset, "Parsing LSHW failed", LogFormat.PlainText,
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

  protected def parseLldp(asset: Asset, parser: LldpParser): Status[LldpRepresentation] = {
    parser.parse() match {
      case Left(ex) =>
        AssetLog.notice(asset, "Parsing LLDP failed", LogFormat.PlainText,
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
        msg,
        LogFormat.PlainText,
        LogSource.Internal
      ).withException(e).create()
    } catch {
      case ex =>
        logger.error("Database problems", ex)
        SystemTattler.safeError("Database problems: %s".format(ex.getMessage))
    }
    e
  }
}
