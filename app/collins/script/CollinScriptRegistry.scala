package collins
package script

import com.googlecode.scalascriptengine.RefreshAsynchronously
import com.googlecode.scalascriptengine.ScalaScriptEngine
import com.googlecode.scalascriptengine.Config
import com.googlecode.scalascriptengine.FromClasspathFirst

import java.io.File
import java.net.URLClassLoader

import play.api.Application
import play.api.mvc.Content
import play.api.templates.Html

import scala.collection.JavaConversions._

import util.RichFile._

case class CollinScriptCompileException(script: String, msg: String)
  extends Exception("Compile exception while compiling %s: %s".format(script, msg))


trait ScriptWrapper {

  val DEFAULT_PACKAGE = "collins.script"

  val sourceDir = new File(CollinScriptConfig.scriptDir)
  var compilationClassPath = getAppClasspath
  var runtimeClasspath = getAppClasspath
  val outputDir = new File(System.getProperty("java.io.tmpdir"),
      "collinscript-classes")

  val engine = new ScalaScriptEngine(Config(
      Set(sourceDir), compilationClassPath, runtimeClasspath,
      outputDir)) with RefreshAsynchronously with FromClasspathFirst {
        // each file will only be checked maximum once per second
        val recheckEveryMillis: Long = 1000
      }

  def getAppClasspath: Set[File] = {
    classOf[Application].getClassLoader.asInstanceOf[URLClassLoader].getURLs()
      .map{ url => new File(url.getPath) }.toSet
  }

  /**
   * Calls a CollinScript method specified as a string, using the supplied
   * arguments to determine the manner in which it gets called.
   *
   * @param method a String containing a CollinScript method to call.
   * @param args the arguments to pass to the CollinScript method.
   * @return the results of the method call.
   */
  def callMethod(method: String, args: AnyRef*): AnyRef = {
    val parameterClasses = args.map{ arg => arg.getClass }
    val methodSplit = method.split("\\.")
    val methodClass = methodSplit.slice(0, methodSplit.length - 1)
      .reduceLeft(_ + "." + _)
    val classMethod = methodSplit(methodSplit.length - 1)
    engine.get[AnyRef](methodClass).getMethod(classMethod,
        parameterClasses : _*).invoke(this, args : _*)
  }

}


object CollinScriptRegistry extends ScriptWrapper {
  
  def initializeAll(app: Application): Unit = {
    outputDir.mkdir
    engine.deleteAllClassesInOutputDirectory
    engine.refresh
  }

}
