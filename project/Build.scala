import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

    val appName         = "collins"
    val appVersion      = "1.2.5"

    val appDependencies = Seq()

    val main = play.Project(appName, appVersion, appDependencies).settings()

}
