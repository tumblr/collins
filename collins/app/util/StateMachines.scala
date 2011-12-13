package util

import models.{Asset, IpmiInfo, Status}
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
      Asset.update(asset.copy(status = stateToStatus().id, updated = Some(new Date())))
      IpmiInfo.delete("asset_id={id}").on('id -> asset.getId).executeUpdate()
    case _ =>
      Asset.update(asset.copy(status = stateToStatus().id, updated = Some(new Date())))
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

object StateMachine {
  case class InvalidStateTransition(fromState: String, command: String) extends
    Exception("Transitioning from " + fromState + " via command " + command)
}

trait StateMachine {
  import StateMachine._

  protected abstract class State
  protected var state: State = _

  protected def transition[A](command: String)(f: PartialFunction[State, A]) = synchronized {
    if (f.isDefinedAt(state)) {
      f(state)
    } else {
      throw new InvalidStateTransition(state.toString, command)
    }
  }
}
