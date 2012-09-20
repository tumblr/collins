package collins
package script

import models.{Asset, Page}
import models.asset.AssetView
import util.conversions._

import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import java.util.concurrent.locks.ReentrantReadWriteLock
import play.api.{Application, Logger, Play}
import scala.collection.JavaConversions._
import scala.tools.nsc.io.AbstractFile

import com.googlecode.scalascriptengine.{CodeVersion, Config, FromClasspathFirst, ScalaScriptEngine}
import com.googlecode.scalascriptengine.RefreshAsynchronously


case class CollinScriptCompileException(script: String, msg: String)
  extends Exception("Compile exception while compiling %s: %s".format(script, msg))


/**
 * A trait which provides for compiling and executing arbitrary Scala scripts,
 * providing access to the Collins namespace at runtime.
 */
sealed trait CollinScriptEngine {

  protected val logger = Logger("CollinScriptEngine")
  protected val refreshLock: ReentrantReadWriteLock =
    new ReentrantReadWriteLock()

  protected var engine = createEngine

  protected var lastRefreshMillis: AtomicLong = new AtomicLong(0)

  /**
   * Calls a CollinScript method specified on an Object as a string,
   * using the supplied arguments to determine the manner in which it gets
   * called.
   *
   * @param method a String containing a CollinScript method to call.
   * @param args the arguments to pass to the CollinScript method.
   * @return the results of the method call.
   */
  def callMethod(method: String, args: AnyRef*): AnyRef = {
    logger.debug("CollinScript method call: %s, args: %s".format(method,
        args.mkString(", ")))
    if (!enabled) {
      logger.warn("CollinScript is not enabled but callMethod(%s) called."
          .format(method))
      return None
    }
    tryRefresh
    val argumentClasses = args.map{ arg => arg.getClass }
    val methodSplit = method.split("\\.")
    val objectClass = methodSplit.slice(0, methodSplit.length - 1)
      .mkString(".")
    val classMethod = methodSplit(methodSplit.length - 1)
    try {
      engine.get[CollinScript](objectClass).getMethod(classMethod,
          argumentClasses : _*).invoke(this, args : _*)
    } catch {
      case e => {
        logger.error("COLLINSCRIPT EXECUTION ERROR:\n%s".format(
            e.getTraceAsString))
        None
      }
    }
  }

  protected def createEngine() = new ScalaScriptEngine(
      Config(Set(sourceDir), getAppClasspath, Set[File](), outputDir))
      with FromClasspathFirst {}

  protected def enabled = CollinScriptConfig.enabled

  /**
   * Returns the classpath used by the Collins application.
   *
   * @return a Set of File objects representing all search locations on the
   *   classpath
   */
  protected def getAppClasspath: Set[File] = {
    try {
      import Play.current
      current.classloader.asInstanceOf[URLClassLoader].getURLs()
        .map{ url => new File(url.getPath) }.toSet
    } catch {
      case e => classOf[CollinScript].getClassLoader.asInstanceOf[URLClassLoader]
        .getURLs().map{ url => new File(url.getPath) }.toSet
    }
  }

  protected def outputDir = new File(CollinScriptConfig.outputDir)

  protected def refreshPeriodMillis = CollinScriptConfig.refreshPeriodMillis

  protected def sourceDir = new File(CollinScriptConfig.scriptDir)

  /**
   * Attempts to refresh code that has been changed on the filesystem,
   * defaulting to the latest successfully-compiled code version if an error
   * occurs.
   */
  def tryRefresh: Unit = {
    val oldClassloader = Thread.currentThread().getContextClassLoader
    try {
      // Sets the current thread's context Classloader to Play's current
      // classloader, as SSE derives its classloader from this thread's context.
      Thread.currentThread().setContextClassLoader(Play.current.classloader)
      // If the time of last refresh is less than the refresh threshold, don't
      // refresh the code unless we're in a startup state.
      if (System.currentTimeMillis - lastRefreshMillis.get <
          refreshPeriodMillis) {
        return
      }
      // If the refresh lock is locked, we're currently refreshing, so
      // terminate script refresh attempt.
      if (refreshLock.isWriteLocked) {
        return
      }
      logger.debug("Refreshing CollinScript engine...")
      // Engine is not threadsafe, so refresh by way of write locks.
      refreshLock.writeLock.lock
      engine.refresh
      refreshLock.writeLock.unlock
      lastRefreshMillis.set(System.currentTimeMillis)
    } catch {
      case e => {
        logger.error("COLLINSCRIPT COMPILATION ERROR:\n%s".format(
           e.getTraceAsString))
      }
    } finally {
      // Restore this thread's context Classloader to that which it was
      // previously using.
      Thread.currentThread().setContextClassLoader(oldClassloader)
    }
  }

}


object CollinScriptRegistry extends CollinScriptEngine {

  def initializeAll(app: Application) = {
    outputDir.mkdir
    engine.deleteAllClassesInOutputDirectory
    tryRefresh
  }

  def shutdown = {
    // Recursively deletes output class directory.
    AbstractFile.getFile(outputDir).delete
  }

}
