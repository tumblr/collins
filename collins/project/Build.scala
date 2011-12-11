import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "collins"
    val appVersion      = "1.0"

    val appDependencies = Seq(
      "org.bouncycastle" % "bcprov-jdk16" % "1.46"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
      // Add your own project settings here      
    )

}
