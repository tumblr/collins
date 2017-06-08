package collins.controllers.actions.ipmi

import scala.concurrent.Future

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.JsArray
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString

import collins.controllers.ResponseData
import collins.controllers.SecureController
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.controllers.actions.ipaddress.AddressActionHelper
import collins.models.IpmiInfo
import collins.models.shared.AddressPool
import collins.util.security.SecuritySpecification

case class GetPoolsAction(
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AddressActionHelper {

  case class ActionDataHolder(pools: Set[String]) extends RequestDataHolder

  override def validate(): Validation = {
    var pools: Set[String] =
      IpmiInfo.AddressConfig.map(_.poolNames) match {
        case x if !x.isEmpty  => x.getOrElse(Set())
        case _                => Set(IpmiInfo.AddressConfig.flatMap(_.defaultPoolName).getOrElse(""))
      }

    Right(ActionDataHolder(pools))
  }

  override def execute(rd: RequestDataHolder) = Future {
    rd match {
      case ActionDataHolder(pools) => {
        format(pools)
      }
    }
  }

  protected def format(pools: Set[String]) = {
    // name, gateway, network, startAddress
    val addressPools = toAddressPools(pools)
    val seq = Seq(
      "POOLS" -> toJsonList(addressPools)
    )
    val jsObject = JsObject(seq)
    ResponseData(Status.Ok, jsObject)
  }

  protected def toJsonList(pools: Set[AddressPool]): JsArray = JsArray(
    pools.toList.map { pool =>
      val seq = Seq(
        "NAME" -> JsString(convertPoolName(pool.name)),
        "NETWORK" -> JsString(formatNetworkAddress(pool.network)),
        "START_ADDRESS" -> JsString(pool.startAddress.getOrElse("Unspecified")),
        "SPECIFIED_GATEWAY" -> JsString(pool.gateway.getOrElse("Unspecified")),
        "GATEWAY" -> JsString(pool.gateway.getOrElse(pool.ipCalc.minAddress)),
        "BROADCAST" -> JsString(pool.ipCalc.broadcastAddress),
        "POSSIBLE_ADDRESSES" -> JsNumber(pool.ipCalc.addressCount)
      )
      JsObject(seq)
    }
  )

  protected def formatNetworkAddress(network: String): String = network match {
    case o if o == AddressPool.MagicNetwork =>
      "Possible legacy or untracked address pool"
    case g => g
  }

  protected def toAddressPools(pools: Set[String]): Set[AddressPool] = {
   // Check to see if a default pool is configured from the parent level
   // ipmi config block. If no ipmi pools are configured the default pool
   // will be returned under the pool name DEFAULT. If pools are configured
   // any ipmi configuration at the parent level will be ignored. This is
   // because we can't ensure that you have not overwritten the DEFAULT pool
   // in one of your pools or have set the defaultPool param which would cause
   // duplicate pools to be returned. The parent level ipmi config would
   // not be accessible anyway due to the way default pool is checked for
   // in the IpAddressConfig class.
   val defaultPool: Option[AddressPool] =
     IpmiInfo.AddressConfig.map(_.pools.isEmpty).getOrElse(false) match {
       case true => IpmiInfo.AddressConfig.flatMap(_.defaultPool)
       case _ => None
     }

   val poolSet: Set[AddressPool] =
    IpmiInfo.AddressConfig.map { cfg =>
      pools.map { pool =>
        cfg.pool(pool).getOrElse {
          val poolName = AddressPool.poolName(pool)
          AddressPool(poolName, AddressPool.MagicNetwork, None, None)
        }
      }
    }.getOrElse(Set())

    defaultPool.isDefined match {
      case true => Set(defaultPool.get)
      case _ => poolSet
    }
  }

}
