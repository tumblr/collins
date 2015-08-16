package collins.callbacks

abstract sealed class CallbackActionType
object CallbackActionType {
  object Exec extends CallbackActionType

  def apply(name: String): Option[CallbackActionType] = name.toLowerCase match {
    case "exec"   => Some(Exec)
    case _        => None
  }
}
