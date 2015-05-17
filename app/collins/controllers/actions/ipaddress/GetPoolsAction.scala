package collins.controllers.actions.ipaddress

import scala.concurrent.Future

import play.api.libs.json.JsArray
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import collins.controllers.ResponseData
import collins.controllers.SecureController
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.models.IpAddresses
import collins.models.Truthy
import collins.models.shared.AddressPool
import collins.util.security.SecuritySpecification

// Get pools, all or just those in use
case class GetPoolsAction(
  allPools: Truthy,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AddressActionHelper {

  case class ActionDataHolder(all: Boolean) extends RequestDataHolder

  override def validate(): Validation = Right(ActionDataHolder(allPools.toBoolean))

  override def execute(rd: RequestDataHolder) = Future { 
    rd match {
      case ActionDataHolder(all) => 
        val pools: Set[String] =
          if (all) {
            val set = IpAddresses.AddressConfig.map(_.poolNames).getOrElse(Set())
            set | IpAddresses.getPoolsInUse()
          } else {
            IpAddresses.getPoolsInUse()
          }
        format(pools)
    }
  }

  protected def format(pools: Set[String]) = {
    // name, gateway, network, startAddress
    val addressPools = toAddressPools(pools)
    val seq = Seq("POOLS" -> toJsonList(addressPools))
    val jsObject = JsObject(seq)
    ResponseData(Status.Ok, jsObject)
  }

  protected def toJsonList(pools: Set[AddressPool]): JsArray = JsArray(
    pools.toList.map { pool =>
      val seq = Seq(
        "NAME" -> JsString(convertPoolName(pool.name, true)),
        "NETWORK" -> JsString(formatNetworkAddress(pool.network)),
        "START_ADDRESS" -> JsString(pool.startAddress.getOrElse("Unspecified")),
        "SPECIFIED_GATEWAY" -> JsString(pool.gateway.getOrElse("Unspecified")),
        "GATEWAY" -> JsString(pool.ipCalc.minAddress),
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

  protected def toAddressPools(pools: Set[String]): Set[AddressPool] =
    IpAddresses.AddressConfig.map { cfg =>
      pools.map { pool =>
        cfg.pool(pool).getOrElse {
          val poolName = AddressPool.poolName(pool)
          AddressPool(poolName, AddressPool.MagicNetwork, None, None)
        }
      }
    }.getOrElse(Set())

}
