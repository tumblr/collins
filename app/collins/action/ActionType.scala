package collins
package action


abstract sealed class ActionType


object ActionType {

  object Exec extends ActionType
  object Script extends ActionType

  def apply(name: String): Option[ActionType] = name.toLowerCase match {
    case "exec" => Some(Exec)
    case "script" => Some(Script)
    case _ => None
  }

}
