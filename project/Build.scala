import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    //TODO move this crap into build.sbt
    val appName         = "collins"
    val appVersion      = "1.2.4-SNAPSHOT"

    val appDependencies = Seq()

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings()

}
