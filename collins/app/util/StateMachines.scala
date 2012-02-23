package util

import models.{Asset, AssetMetaValue, IpmiInfo, Status}
import models.conversions._

import com.twitter.util.StateMachine
import java.util.Date
import java.sql._

object AssetStateMachine {
  def apply(asset: Asset) = new AssetStateMachine(asset)
}
class AssetStateMachine(asset: Asset) extends StateMachine {
  import Status.Enum._
  import StateMachine.InvalidStateTransition

  sealed abstract class AssetState(val status: Status.Enum) extends State
  case class IncompleteState() extends AssetState(Incomplete)
  case class NewState() extends AssetState(New)
  case class UnallocatedState() extends AssetState(Unallocated)
  case class AllocatedState() extends AssetState(Allocated)
  case class CancelledState() extends AssetState(Cancelled)
  case class MaintenanceState() extends AssetState(Maintenance)
  case class DecommissionedState() extends AssetState(Decommissioned)

  state = statusToState()

  def decommission() = transition("decommission") {
    case UnallocatedState() =>
      state = DecommissionedState()
      this
    case NewState() =>
      state = DecommissionedState()
      this
    case CancelledState() =>
      state = DecommissionedState()
      this
  }

  def cancel() = transition("cancel") {
    case NewState() =>
      state = CancelledState()
      this
    case UnallocatedState() =>
      state = CancelledState()
      this
  }

  def maintenance() = transition("maintenance") {
    case _ =>
      state = MaintenanceState()
      this
  }

  def update() = transition("update") {
    case IncompleteState() =>
      state = NewState()
      this
    case NewState() =>
      state = UnallocatedState()
      this
    case UnallocatedState() =>
      state = AllocatedState()
      this
    case AllocatedState() =>
      state = UnallocatedState()
      this
  }

  def executeUpdate()(implicit con: Connection) = transition("executeUpdate") {
    case DecommissionedState() =>
      Asset.update(asset.copy(status = stateToStatus().id, updated = Some(new Date().asTimestamp)))
      IpmiInfo.delete("asset_id={id}").on('id -> asset.getId).executeUpdate()
      AssetMetaValue.delete("asset_id={id}").on('id -> asset.getId).executeUpdate()
    case _ =>
      Asset.update(asset.copy(status = stateToStatus().id, updated = Some(new Date().asTimestamp)))
  }

  private def stateToStatus() = state match {
    case s: AssetState => s.status
    case _ => throw new IllegalStateException("State not AssetState")
  }

  private def statusToState() = Status.Enum(asset.getStatus().getId) match {
    case Incomplete => IncompleteState()
    case New => NewState()
    case Unallocated => UnallocatedState()
    case Allocated => AllocatedState()
    case Cancelled => CancelledState()
    case Maintenance => MaintenanceState()
    case Decommissioned => DecommissionedState()
    case e => throw new IllegalStateException("Unknown asset status found: " + e)
  }
}

// Manages somewhat specific transitions for a given environment
class SoftLayerStateManager extends com.tumblr.play.state.Manager {
  type T = Asset

  import models.{AssetLifecycle, AssetType, MetaWrapper, Model}
  override def transition(old: Asset, current: Asset): Unit = {
    val types = Map("SERVER_NODE" -> Set("PRIMARY_ROLE","POOL"))
    AssetType.findById(current.asset_type)
      .map(at => AssetType.Enum(at.getId))
      .filter(at => types.contains(at.toString))
      .filter(_ => current.status == 2)
      .filter { at =>
        types(at.toString).foldLeft(0) { case(found, metaName) =>
          found + MetaWrapper.findMeta(current, metaName).map(_ => 1).getOrElse(0)
        } == types(at.toString).size
      }
      .foreach { t =>
        val options = Map(
          "status" -> "Allocated",
          "reason" -> "Triggered by rule"
        )
        AssetLifecycle.updateAssetStatus(current, options)
      }
  }
  override def canTransition(a: AnyRef): Boolean = {
    a.isInstanceOf[Asset]
  }
}
