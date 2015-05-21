import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

    val appName         = "collins"
    val appVersion      = "1.4.0"

    val appDependencies = Seq()

    val main = play.Project(appName, appVersion, appDependencies).settings(
      templatesImport ++= Seq(
        "collins.models._", 
        "collins.models.shared._", 
        "collins.models.asset._", 
        "collins.models.lldp._", 
        "collins.models.lshw._", 
        "collins.models.logs._",
        "collins.controllers._") 
    )

}
