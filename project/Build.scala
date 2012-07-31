import sbt._
import Keys._
import PlayProject._

import java.io.File

object ApplicationBuild extends Build {

    val appName         = "collins"
    val appVersion      = "1.1"

    val appDependencies = Seq(
      "commons-net" % "commons-net" % "3.1",
      "org.apache.commons" % "commons-lang3" % "3.1",
      "org.bouncycastle" % "bcprov-jdk16" % "1.46",
      "com.twitter" %% "util-core" % "1.12.12",
      "com.twitter" %% "finagle-http" % "1.10.0",
      "org.jsoup" % "jsoup" % "1.6.1",
      "org.squeryl" %% "squeryl" % "0.9.5",
      "org.yaml" % "snakeyaml" % "1.10"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
    )

}
