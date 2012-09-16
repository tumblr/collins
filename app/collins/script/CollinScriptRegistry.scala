package collins
package script

import models.Asset
import models.asset.AssetView
import util.conversions._

import java.io.File
import java.net.URLClassLoader
import play.api.Application
import play.api.Logger
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

  protected val compileClasspath = getAppClasspath
  protected val outputDir = new File(System.getProperty("java.io.tmpdir"),
      "collinscript-classes")
  protected val refreshPeriodMillis = CollinScriptConfig.refreshPeriodMillis
  protected val runtimeClasspath = getAppClasspath
  protected val sourceDir = new File(CollinScriptConfig.scriptDir)

  protected var classPathHash = getAppClasspath.hashCode
  protected var enabled = CollinScriptConfig.enabled
  protected var lastRefreshMillis: Long = 0
  protected var numRefreshes = 0

  protected var engine = new ScalaScriptEngine(
      Config(Set(sourceDir), getAppClasspath, getAppClasspath, outputDir))
      with FromClasspathFirst {}

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
    // Derives argument classes and calls method on the specified Object.
    // AssetView objects will be typed as Assets, and only AssetView
    // objects may be passed to scripts, so cast appropriately.
    val parameterClasses = args.map{ arg => arg.getClass }
    val methodSplit = method.split("\\.")
    val objectClass = methodSplit.slice(0, methodSplit.length - 1)
      .mkString(".")
    val classMethod = methodSplit(methodSplit.length - 1)
    try {
      engine.get[CollinScript](objectClass).getMethod(classMethod,
          parameterClasses : _*).invoke(engine, args : _*)
    } catch {
      case e => {
        logger.error("COLLINSCRIPT EXECUTION ERROR:\n%s".format(
            e.getTraceAsString))
        None
      }
    }

  }

  /**
   * Returns the classpath used by the Collins application.
   *
   * @return a Set of File objects representing all search locations on the
   *   classpath
   */
  protected def getAppClasspath: Set[File] = {
    try {
      import play.api.Play.current
      current.classloader.asInstanceOf[URLClassLoader].getURLs()
        .map{ url => new File(url.getPath) }.toSet
    } catch {
      case e => classOf[CollinScript].getClassLoader.asInstanceOf[URLClassLoader]
        .getURLs().map{ url => new File(url.getPath) }.toSet
    }
  }

  /**
   * Attempts to refresh code that has been changed on the filesystem,
   * defaulting to the latest successfully-compiled code version if an error
   * occurs.
   */
  def tryRefresh: Unit = {
    try {
      // If the time of last refresh is less than the refresh threshold, don't
      // refresh the code unless we're in a startup state.
      if (System.currentTimeMillis - lastRefreshMillis < refreshPeriodMillis
          && numRefreshes >= 2) {
        return
      }
      // Checks for classpath changes; if so, instantiates a new engine to
      // force a recompilation against new Collins code.  This must be done
      // twice at startup to preclude linking issues against any
      // partially-compiled sources.
      val currentClassPath = getAppClasspath
      val currentClassPathHash = currentClassPath.hashCode
      if (currentClassPathHash != classPathHash || numRefreshes < 2) {
        engine = new ScalaScriptEngine(
            Config(Set(sourceDir), getAppClasspath, getAppClasspath, outputDir))
            with FromClasspathFirst {}
        classPathHash = currentClassPathHash
        numRefreshes += 1
      }
      logger.debug("Refreshing CollinScript engine...")
      engine.refresh
      lastRefreshMillis = System.currentTimeMillis
    } catch {
      case e => {
        logger.error("COLLINSCRIPT COMPILATION ERROR:\n%s".format(
           e.getTraceAsString))
      }
    }
  }

}


object CollinScriptRegistry extends CollinScriptEngine {

  def initializeAll(app: Application): Unit = {
    outputDir.mkdir
    engine.deleteAllClassesInOutputDirectory
    tryRefresh
  }

  def shutdown = {
    // Recursively deletes output class directory.
    AbstractFile.getFile(outputDir).delete
  }

}
