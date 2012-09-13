package collins
package script

import util.conversions._

import java.io.File
import java.net.URLClassLoader
import play.api.Application
import play.api.Logger
import scala.collection.JavaConversions._
import scala.tools.nsc.io.AbstractFile

import com.googlecode.scalascriptengine.{Config, FromClasspathFirst, ScalaScriptEngine}



case class CollinScriptCompileException(script: String, msg: String)
  extends Exception("Compile exception while compiling %s: %s".format(script, msg))


/**
 * A trait which provides for compiling and executing arbitrary Scala scripts,
 * providing access to the Collins namespace at runtime.
 */
sealed trait CollinScriptEngine {

  protected val logger = Logger("ScriptEngine")

  val compileClasspath = getAppClasspath
  val outputDir = new File(System.getProperty("java.io.tmpdir"),
      "collinscript-classes")
  val runtimeClasspath = getAppClasspath
  val sourceDir = new File(CollinScriptConfig.scriptDir)

  var enabled = CollinScriptConfig.enabled
  val engine = new ScalaScriptEngine(
      Config(Set(sourceDir), compileClasspath, runtimeClasspath, outputDir))
      with FromClasspathFirst {
        val recheckEveryMillis: Long = CollinScriptConfig.refreshPeriod
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
      Logger.logger.warn("CollinScript is not enabled but callMethod(%s) called."
          .format(method))
      return None
    }
    tryRefresh
    // Derives argument classes and calls method on the specified Object.
    val parameterClasses = args.map{ arg => arg.getClass }
    val methodSplit = method.split("\\.")
    val objectClass = methodSplit.slice(0, methodSplit.length - 1)
      .mkString(".")
    val classMethod = methodSplit(methodSplit.length - 1)
    try {
      engine.get[AnyRef](objectClass).getMethods.foreach{ meth =>
        logger.debug("METHOD NAME: %s".format(meth.getName))
        logger.debug("PARAMS: %s".format(meth.getTypeParameters.map{param => param.getName}.toSet))
      }
      val retVal = engine.get[AnyRef](objectClass).getMethod(classMethod,
          parameterClasses : _*).invoke(this, args : _*)
      if (retVal != None) {
        logger.debug("CollinScript return value: %s".format(retVal.toString))
      } else {
        logger.debug("CollinScript returned None.")
      }
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
    classOf[CollinScript].getClassLoader.asInstanceOf[URLClassLoader].getURLs()
      .map{ url => new File(url.getPath) }.toSet
  }

  /**
   * Attempts to refresh code that has been changed on the filesystem,
   * defaulting to the latest successfully-compiled code version if an error
   * occurs.
   */
  protected def tryRefresh: Unit = {
    try {
      logger.debug("Refreshing CollinScript engine...")
      engine.refresh
    } catch {
      case e => {
        Logger.logger.error("COLLINSCRIPT COMPILATION ERROR:\n%s"
            .format(e.getTraceAsString))
        return
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
