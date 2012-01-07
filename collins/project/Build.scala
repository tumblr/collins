import sbt._
import Keys._
import PlayProject._

import java.io.File

object ApplicationBuild extends Build {

    val appName         = "collins"
    val appVersion      = "1.0"

    val appDependencies = Seq(
      "org.bouncycastle" % "bcprov-jdk16" % "1.46",
      "com.twitter" %% "util-core" % "1.12.8"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
      resourceDirectories in Compile += file("db")
    )

}
