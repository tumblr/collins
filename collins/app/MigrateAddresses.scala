import models._
import util.{IpAddress, IpAddressCalc}
import org.squeryl.PrimitiveTypeMode._

import java.io.File
import play.api.{Application, Mode, Play}

object MigrateAddresses extends App {

  val IdsForMigration = Set(33L, 48L, 49L)

  if (args.length != 1) {
    sys.error("Pass the directory containing your top level code")
  }
  val application = new Application(new File(args(0)), this.getClass.getClassLoader, None, Mode.Dev)
  Play.start(application)
  Model.initialize()

  def gatewayForIp(address: String, netmask: String): String = {
    val calc = IpAddressCalc(address, netmask, None)
    calc.minAddress
  }
  def netmaskForIp(address: String): String = {
    if (address.startsWith("10.")) {
      "255.255.248.0"
    } else {
      "255.255.255.224"
    }
  }

  def runMigration(): Unit = AssetMetaValue.inTransaction {
    Console.println("Migrating old address information to new ip storage format")
    val mvs = from(AssetMetaValue.tableDef)(a =>
      where(a.asset_meta_id in IdsForMigration)
      select(a)
    ).toList
    val assets = mvs.groupBy(_.asset_id).map { case(aid, amvs) =>
      (aid -> amvs.map(_.value).filter(_.nonEmpty).toSet)
    }
    assets.foreach { case(aid, ips) =>
      val asset = Asset.findById(aid).get
      println("%d - %s".format(aid, ips.mkString(",")))
      ips.foreach { ip =>
        val netmask = netmaskForIp(ip)
        val gateway = gatewayForIp(ip, netmask)
        println("IP: %s (%d) - Gateway: %s - Netmask: %s".format(
          ip, IpAddress.toLong(ip), gateway, netmask
        ))
        try {
          val address = IpAddresses(aid, IpAddress.toLong(gateway), IpAddress.toLong(ip), IpAddress.toLong(netmask))
          IpAddresses.create(address)
        } catch {
          case e =>
            if (asset.getStatus().name != "Cancelled" && asset.getStatus().name != "Decommissioned") {
              throw e
            }
        }
      }
      AssetMetaValue.deleteByAssetAndMetaId(asset, IdsForMigration)
    }
    AssetMetaValue.tableDef.deleteWhere(a => a.asset_meta_id === 32L)
    (IdsForMigration ++ Set(32L)).foreach { id =>
      AssetMeta.findById(id) match {
        case Some(amid) => AssetMeta.delete(amid)
        case None =>
      }
    }
  }

  try {
    Thread.sleep(100)
    runMigration()
    System.exit(0)
  } catch {
    case e =>
      println(e)
      System.exit(1)
  } finally {
      Model.shutdown()
      Play.stop()
  }

}
