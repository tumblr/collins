package collins
package action

import action.executor.{ExecActionExecutor, ScriptActionExecutor}
import action.handler.{AssetActionHandler, AssetExecActionHandler, AssetScriptActionHandler, AssetsActionHandler, AssetsExecActionHandler, AssetsScriptActionHandler, CallbackActionHandler, CallbackExecActionHandler, CallbackScriptActionHandler}
import play.api.Logger
import util.config.ActionConfig


/**
 * A convenience object for retrieving the Collins Action handler corresponding
 * to a Collins object.
 */
object Action {
  
  /**
   * Creates a Handler for a Collins object based upon what has been defined in
   * a util.action.ActionConfig user configuration object.
   *
   * @param cfg an ActionConfig describing an action to execute.
   * @return an ActionHandler subclass handling Collins Actions for an object.
   */
  def getHandler[H <: ActionHandler] (cfg: ActionConfig)(implicit mf: Manifest[H]): Option[H] = {
    // Determines the type of handler requested and returns one mixed in with
    // an AssetExecutor as specified by the action's type, appropriate to the
    // handler's context.
    mf match {
      case handler if handler <:< manifest[AssetActionHandler] =>
        Some(cfg.actionType match {
          case ActionType.Exec =>
            AssetExecActionHandler(cfg.command).asInstanceOf[H]
          case ActionType.Script =>
            AssetScriptActionHandler(cfg.command).asInstanceOf[H]
        })
      case handler if handler <:< manifest[AssetsActionHandler] =>
        Some(cfg.actionType match {
          case ActionType.Exec =>
            AssetsExecActionHandler(cfg.command).asInstanceOf[H]
          case ActionType.Script =>
            AssetsScriptActionHandler(cfg.command).asInstanceOf[H]
        })
      case handler if handler <:< manifest[CallbackActionHandler] =>
        Some(cfg.actionType match {
          case ActionType.Exec =>
            CallbackExecActionHandler(cfg.command).asInstanceOf[H]
          case ActionType.Script =>
            CallbackScriptActionHandler(cfg.command).asInstanceOf[H]
        })
      case _ => None
    }
  }

}
