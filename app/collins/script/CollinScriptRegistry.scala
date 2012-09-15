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

import com.googlecode.scalascriptengine.{Config, FromClasspathFirst, ScalaScriptEngine}
import com.googlecode.scalascriptengine.RefreshAsynchronously


case class CollinScriptCompileException(script: String, msg: String)
  extends Exception("Compile exception while compiling %s: %s".format(script, msg))


/**
 * A trait which provides for compiling and executing arbitrary Scala scripts,
 * providing access to the Collins namespace at runtime.
 */
sealed trait CollinScriptEngine {

  protected val logger = Logger("CollinScriptEngine")

  val compileClasspath = getAppClasspath
  val outputDir = new File(System.getProperty("java.io.tmpdir"),
      "collinscript-classes")
  val runtimeClasspath = getAppClasspath
  val sourceDir = new File(CollinScriptConfig.scriptDir)

  var enabled = CollinScriptConfig.enabled
  val engine = new ScalaScriptEngine(
      Config(Set(sourceDir), compileClasspath, runtimeClasspath, outputDir))
      with RefreshAsynchronously with FromClasspathFirst {
        val recheckEveryMillis: Long = CollinScriptConfig.refreshPeriodMillis
      }

  def toList[a](array: Array[a]): List[a] = {
    if (array == null || array.length == 0) Nil
    else if (array.length == 1) List(array(0))
    else array(0) :: toList(array.slice(1, array.length))
  }

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
    logger.error("OBJECT CLASS: %s".format(objectClass))
    logger.error("CLASS METHOD: %s".format(classMethod))
    logger.error("OBJECT METHODS: %s".format(toList(engine.get[AnyRef](objectClass).getMethods)))
    try {
      val retVal = engine.get[AnyRef](objectClass).getMethod("decorateTag", models.Asset.getClass).invoke(this, args: _*)

      //engine.get[CollinScript](objectClass).getMethod(classMethod,
          //classOf[Asset]).invoke(engine, args : _*)
      retVal
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
    classOf[Asset].getClassLoader.asInstanceOf[URLClassLoader].getURLs()
      .map{ url => new File(url.getPath) }.toSet
  }

  /**
   * Attempts to refresh code that has been changed on the filesystem,
   * defaulting to the latest successfully-compiled code version if an error
   * occurs.
   */
  def tryRefresh: Unit = {
    try {
      logger.debug("Refreshing CollinScript engine...")
      engine.refresh
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
