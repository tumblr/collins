import sbt._
import Keys._
import PlayProject._

import java.io.File

object ApplicationBuild extends Build {

    val appName         = "collins"
    val appVersion      = "1.0"

    val appDependencies = Seq(
      "org.bouncycastle" % "bcprov-jdk16" % "1.46",
      "com.twitter" %% "util-core" % "1.12.12",
      "com.twitter" %% "finagle-http" % "1.10.0"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
    )

}
