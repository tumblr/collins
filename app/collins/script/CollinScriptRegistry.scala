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
sealed trait ScriptEngine {

  val enabled = CollinScriptConfig.enabled
  val compileClasspath = getAppClasspath
  val outputDir = new File(System.getProperty("java.io.tmpdir"),
      "collinscript-classes")
  val runtimeClasspath = getAppClasspath
  val sourceDir = new File(CollinScriptConfig.scriptDir)

  val engine = new ScalaScriptEngine(Config(
      Set(sourceDir), compileClasspath, runtimeClasspath,
      outputDir)) with FromClasspathFirst {
        val recheckEveryMillis: Long = CollinScriptConfig.refreshPeriod
      }
  engine.refresh

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
    if (!enabled) {
      Logger.logger.warn("CollinScript is not enabled but callMethod(%s) called."
          .format(method))
      return None
    }
    // Refreshes changed code, catches and logs compilation errors,
    // defaulting to latest successfully-compiled code version if this is so.
    try {
      engine.refresh
    } catch {
      case e => {
        Logger.logger.error("CollinScript compilation error:\n%s"
            .format(e.getTraceAsString))
      }
    }
    // Derives argument classes and calls method on the specified Object.
    val parameterClasses = args.map{ arg => arg.getClass }
    val methodSplit = method.split("\\.")
    val objectClass = methodSplit.slice(0, methodSplit.length - 1)
      .mkString(".")
    val classMethod = methodSplit(methodSplit.length - 1)
    engine.get[AnyRef](objectClass).getMethod(classMethod,
        parameterClasses : _*).invoke(this, args : _*)
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

}


object CollinScriptRegistry extends ScriptEngine {

  def initializeAll(app: Application): Unit = {
    outputDir.mkdir
    engine.deleteAllClassesInOutputDirectory
  }

  def shutdown = {
    // Recursively deletes output class directory.
    AbstractFile.getFile(outputDir).delete
  }

}
