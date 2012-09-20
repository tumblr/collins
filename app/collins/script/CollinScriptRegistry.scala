package collins
package script

import models.{Asset, Page}
import models.asset.AssetView
import util.conversions._

import java.io.File
import java.lang.reflect.Method
import java.net.URLClassLoader
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import java.util.concurrent.locks.ReentrantReadWriteLock
import play.api.{Application, Logger, Play}
import scala.collection.JavaConversions._
import scala.tools.nsc.io.AbstractFile
import scala.util.control.Breaks._

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

  protected var engine: ScalaScriptEngine with FromClasspathFirst = null

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
    // Derives method argument classes, CollinScript object, and method to call.
    val argumentClasses = args.map{ arg => arg.getClass }
    val methodSplit = method.split("\\.")
    val objectClass = methodSplit.slice(0, methodSplit.length - 1)
      .mkString(".")
    val classMethod = methodSplit(methodSplit.length - 1)
    try {
      // Iterates through CollinScript methods so polymorphic method signature
      // variations defined in CollinScripts can be called.
      var foundMethod: Method = null
      engine.get[CollinScript](objectClass).getMethods.foreach( method =>
        if (method.getName == classMethod) {
          // Zips the CollinScript method parameter classes with those which
          // were passed into callMethod, checks if the signatures match up.
          var methodMatches = true
          method.getParameterTypes.zip(argumentClasses).foreach( paramPair =>
            paramPair match {
              case (methodParam, desiredParam) => {
                if (!methodParam.isAssignableFrom(desiredParam)) {
                  methodMatches = false
                }
              }
              case _ => {
                methodMatches = false
              }
            }
          )
          // If the signatures match polymorphically, denote it for invocation.
          if (methodMatches) {
            foundMethod = method
          }
        }
      )
      // If an appropriate CollinScript method was found, invokes it.
      if (foundMethod != null) {
        return foundMethod.invoke(this, args : _*)
      }
      logger.error("No CollinScript method found for call: %s, args: %s"
          .format(method, args.mkString(", ")))
    } catch {
      case e => {
        logger.error("COLLINSCRIPT EXECUTION ERROR:\n%s".format(
            e.getTraceAsString))
      }
    }
    None
  }

  protected def createEngine() = {
    outputDir.mkdir
    engine = new ScalaScriptEngine(
      Config(Set(sourceDir), getAppClasspath, Set[File](), outputDir))
      with FromClasspathFirst {}
    engine.deleteAllClassesInOutputDirectory
  }

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
      // Sets the current thread context's classloader to Play's current
      // classloader, as SSE naively derives this from the current thread's
      // context.  Huge thanks to Typesafe's James Roper for this suggestion!
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
      // Restore this thread context's classloader to that which it was
      // previously using.
      Thread.currentThread().setContextClassLoader(oldClassloader)
    }
  }

}


object CollinScriptRegistry extends CollinScriptEngine {

  def initializeAll(app: Application) = {
    createEngine
    tryRefresh
  }

  def shutdown = {
    // Recursively deletes output class directory.
    AbstractFile.getFile(outputDir).delete
  }

}
