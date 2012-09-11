package collins
package action

import action.executor.{ExecActionExecutor, ScriptActionExecutor}


object Action {
  
  def getExecutor(cfg: ActionConfig): ActionExecutor = {
    cfg.actionType match {
      case Some(ActionType.Exec) =>
        ExecActionExecutor(cfg.command)
      case Some(ActionType.Script) =>
        ScriptActionExecutor(cfg.command)
      case _ =>
        ExecActionExecutor(cfg.command) 
    }
  }

}
