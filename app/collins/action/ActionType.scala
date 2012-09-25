package collins
package action


abstract sealed class ActionType


/**
 * Represents the type of Collins Action as specified by the user in the
 * Collins configuration file as 'type=exec', 'type=script', etc.
 */
object ActionType {

  object Exec extends ActionType
  object Script extends ActionType

  def apply(name: String): Option[ActionType] = name.toLowerCase match {
    case "exec" => Some(Exec)
    case "script" => Some(Script)
    case _ => None
  }

}
