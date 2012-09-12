package collins
package action

import action.executor.{ExecActionExecutor, ScriptActionExecutor}
import util.config.ActionConfig

object Action {
  
  def getExecutor(cfg: ActionConfig): ActionExecutor = {
    cfg.actionType match {
      case ActionType.Exec =>
        ExecActionExecutor(cfg.command)
      case ActionType.Script =>
        ScriptActionExecutor(cfg.command)
      case _ =>
        ExecActionExecutor(cfg.command) 
    }
  }

}
