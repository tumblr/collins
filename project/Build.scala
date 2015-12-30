import sbt._
import Keys._
import play.twirl.sbt.Import._

object ApplicationBuild extends Build {

    val appName         = "collins"
    val appVersion      = "2.0-SNAPSHOT"

    val main = Project(appName, file(".")).enablePlugins(play.PlayScala).settings(
      version := appVersion,
      TwirlKeys.templateImports ++= Seq(
        "collins.models._", 
        "collins.models.shared._", 
        "collins.models.asset._", 
        "collins.models.lldp._", 
        "collins.models.lshw._", 
        "collins.models.logs._",
        "collins.controllers._") 
    )

}
