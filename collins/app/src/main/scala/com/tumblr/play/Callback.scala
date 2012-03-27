package com.tumblr.play

import java.beans.{PropertyChangeEvent, PropertyChangeListener, PropertyChangeSupport}
import play.api.{Application, Configuration, Logger, Plugin}
import scala.collection.mutable.{Map => MutableMap, StringBuilder}
import com.twitter.util.{Future, FuturePool}
import java.util.concurrent.Executors
import scala.sys.process._

trait Callback {
  val propertyName: String
  def handle(pce: PropertyChangeEvent)
}

case class CallbackConfigurationException(source: String, key: String)
  extends Exception("Didn't find key %s in callback configuration for %s".format(key, source))

trait CallbackManagerInterface {
  protected val logger = Logger(getClass)
  protected val pcs = new PropertyChangeSupport(this)

  def fire(propertyName: String, oldValue: AnyRef, newValue: AnyRef) {
    pcs.firePropertyChange(propertyName, oldValue, newValue)
  }

  def on(propertyName: String)(f: PropertyChangeEvent => Unit) {
    pcs.addPropertyChangeListener(propertyName, new PropertyChangeListener {
      override def propertyChange(pce: PropertyChangeEvent): Unit = f(pce)
    })
  }

  protected def loadListeners(): Unit

  protected def removeListeners() {
    for (listener <- pcs.getPropertyChangeListeners()) pcs.removePropertyChangeListener(listener)
  }
}

class CallbackManagerPlugin(app: Application) extends Plugin with CallbackManagerInterface {
  protected[this] val configuration: Option[Configuration] = app.configuration.getConfig("callbacks")
  protected[this] val executor = Executors.newCachedThreadPool()
  protected[this] val pool = FuturePool(executor)

  override def enabled: Boolean = {
    configuration.flatMap { cfg =>
      cfg.getBoolean("enabled")
    }.getOrElse(false)
  }

  // overrides Plugin.onStart
  override def onStart() {
    if (enabled) {
      loadListeners()
    }
  }

  // overrides Plugin.onStop
  override def onStop() {
    removeListeners()
    try executor.shutdown() catch {
      case _ => // swallow this
    }
  }

  override protected def loadListeners(): Unit = {
    configuration.flatMap(_.getConfig("callback")).map { callbacks =>
      val listeners = callbacks.subKeys.foldLeft(Map[String,Callback]()) { case(total,current) =>
        val config = callbacks.getConfig(current).get
        Map(current -> createCallback(current, config)) ++ total
      }
      listeners.foreach { case(propertyName, callback) =>
        on(callback.propertyName) { pce => callback.handle(pce) }
      }
    }
  }

  protected def createCallback(key: String, config: Configuration): Callback = {
    val eventName = config.getString("event").getOrElse {
      throw new CallbackConfigurationException(key, "event")
    }
    val currentConfigMatches = matcherFromConfig(config.getConfig("current"))(_.getNewValue)
    val previousConfigMatches = matcherFromConfig(config.getConfig("previous"))(_.getOldValue)
    val matchDoes = config.getString("matchAction").getOrElse {
      throw new CallbackConfigurationException(key, "matchAction")
    }
    val matchMethod = config.getString("matchMethod").getOrElse("toString")
    val handlesMatch = createMatchHandler(matchDoes, matchMethod)
    val execFn: Function1[PropertyChangeEvent,Unit] = { pce =>
      if (previousConfigMatches(pce) && currentConfigMatches(pce)) {
        handlesMatch(pce)
      }
    }
    new Callback {
      override val propertyName: String = eventName
      override def handle(pce: PropertyChangeEvent) = execFn(pce)
    }
  }

  protected def matcherFromConfig(config: Option[Configuration])(f: PropertyChangeEvent => AnyRef): Function1[PropertyChangeEvent,Boolean] = {
    new Function1[PropertyChangeEvent,Boolean] {
      override def apply(pce: PropertyChangeEvent): Boolean = {
        config.map { cfg =>
          cfg.getString("matchMethod").map { methodName =>
            val value = f(pce)
            if (value != null) {
              try {
                val method = value.getClass().getMethod(methodName)
                method.invoke(value).asInstanceOf[Boolean]
              } catch {
                case e => false
              }
            } else {
              false
            }
          }.getOrElse(true)
        }.getOrElse(true)
      }
    }
  }

  protected def createMatchHandler(cfg: String, method: String): Function1[PropertyChangeEvent,Unit] = {
    val descriptions = cfg.split(" ").toList
    descriptions(0) match {
      case "exec" =>
        createExecHandler(descriptions.drop(1), method)
    }
  }

  protected def createExecHandler(config: List[String], matchMethod: String): Function1[PropertyChangeEvent,Unit] = {
    new Function1[PropertyChangeEvent,Unit] {
      val cmd = config.mkString(" ")
      val methodName = matchMethod

      override def apply(pce: PropertyChangeEvent): Unit = pool[Unit] {
        val command = formatCommand(pce)
        val process = Process(command)
        val stdout = new StringBuilder()
        val stderr = new StringBuilder()
        val exitStatus = try {
          process ! ProcessLogger(
            s => stdout.append(s + "\n"),
            e => stderr.append(e + "\n")
          )
        } catch {
          case e: Throwable =>
            stderr.append(e.getMessage)
            -1
        }
        logger.info("Ran command %s".format(command))
        logger.info("Exit status was %d".format(exitStatus))
        logger.info("Stdout: %s".format(stdout.toString))
        logger.info("Stderr: %s".format(stderr.toString))
      }
      protected def formatCommand(pce: PropertyChangeEvent): String = {
        val value: AnyRef = Option(pce.getNewValue).getOrElse(pce.getOldValue)
        try {
          val method = value.getClass().getMethod(methodName)
          cmd.replace("<%s>".format(methodName), method.invoke(value).toString)
        } catch {
          case e => cmd.replace("<%s>".format(methodName), "")
        }
      }
    }
  }

}
