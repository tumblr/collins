import sbt._
import Keys._
import com.twitter.sbt._
import com.tumblr.sbt._
import CompileThriftScrooge.{scroogeDebug, scroogeVersion, scroogeThriftIncludeFolders}

/**
 * Sbt project files are written in a DSL in scala.
 *
 * The % operator is just turning strings into maven dependency declarations, so lines like
 *     val example = "com.example" % "exampleland" % "1.0.3"
 * mean to add a dependency on exampleland version 1.0.3 from provider "com.example".
 */
object SerxBuild extends Build {
  val serviceUtil = "com.tumblr" %% "service-util" % "3.0.1"
//  val serviceUtil = "com.tumblr" %% "service-util" % "5.1.0"
//  val finagleRedis = "com.twitter" %% "finagle-redis" % TumblrServiceProject.FinagleVersion
  val finagleRedis = "com.twitter" % "finagle-redis" % "4.0.5"
  val finagleZipkin = "com.twitter" % "finagle-zipkin" % "4.0.5"
  // Use TumblrServiceProject.FinagleVersion if you need it

  System.setProperty("SBT_PROXY_REPO", TumblrRepos.TumblrRepo)

  val GeneralSettings = TumblrBasicProject.newSettings ++ Seq[Setting[_]](
    version := "0.0.1-SNAPSHOT"
  )

  val AllScroogeSettings = Seq(
    scroogeVersion := "2.5.2"
  )

  val ScroogeSettings = CompileThriftScrooge.newSettings ++ AllScroogeSettings ++
    inConfig(Test)(CompileThriftScrooge.genThriftSettings ++ AllScroogeSettings) ++
    inConfig(Compile)(CompileThriftScrooge.genThriftSettings ++ AllScroogeSettings)

  val ProjectSettings: Seq[Setting[_]] =
      GeneralSettings ++
      StandardProject.newSettings ++
      TumblrRepos.newSettings ++
      TumblrTestableProject.newSettings ++
      TumblrStandardProject.newSettings

  val scroogeDependencies = Seq(
    "com.twitter" %% "scrooge-runtime" % "1.1.3" intransitive
  )

  lazy val ServiceProject = Project(id = "serx",
                          base = file("."))
      .settings(ProjectSettings:_*)
      .settings(ScroogeSettings:_*)
//      .settings(libraryDependencies ++= Seq(serviceUtil, finagleRedis) ++ scroogeDependencies)
      .settings(libraryDependencies ++= Seq(serviceUtil, finagleRedis, finagleZipkin) ++ scroogeDependencies)

}
