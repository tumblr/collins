package com.tumblr.play.state

import play.api.{Application, Configuration, Logger, PlayException, Plugin}

trait Manager {
  type T
  def transition(old: T, current: T)
  def canTransition(a: AnyRef): Boolean
}

class ManagerPlugin(app: Application) extends Plugin with Manager {
  type T = AnyRef
  protected[this] val configuration: Option[Configuration] = app.configuration.getConfig("statemanager")
  protected[this] val klass: Option[String] = configuration.flatMap(_.getString("class"))
  protected[this] def InvalidConfig(s: Option[String] = None): Exception = PlayException(
    "Invalid Configuration",
    s.getOrElse("statemanager.enabled is true but statemanager.class not specified or invalid"),
    None
  )

  override def enabled: Boolean = {
    configuration.flatMap { cfg =>
      cfg.getBoolean("enabled")
    }.getOrElse(false)
  }

  override def onStart() {
    if (enabled) {
      if (!klass.isDefined) throw InvalidConfig()
      try {
        getStateManager() match {
          case s: Manager =>
          case o: AnyRef =>
            throw InvalidConfig(Some("specified statemanager.class is not a Manager class"))
        }
      } catch {
        case e: PlayException => throw e
        case e => throw InvalidConfig()
      }
    }
  }

  override def transition(old: AnyRef, current: AnyRef) {
    val instance = getStateManager()
    instance match {
      case i: Manager =>
        Seq(old, current).find(e => !i.canTransition(e)) match {
          case None =>
            i.transition(old.asInstanceOf[i.T], current.asInstanceOf[i.T])
          case Some(e) =>
        }
      case o: AnyRef =>
        throw InvalidConfig(Some("specified statemanager.class is not a Manager class"))
    }
  }

  override def canTransition(a: AnyRef) = false

  protected def getStateManager() = {
    this.getClass.getClassLoader.loadClass(klass.get).newInstance()
  }

}

