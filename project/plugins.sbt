resolvers ++= Seq(
    DefaultMavenRepository,
    Resolver.url("Play", url("https://downloads.lightbend.com/ivy-releases/"))(Resolver.ivyStylePatterns),
    "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/",
    "Sontype Releases" at "https://oss.sonatype.org/content/repositories/releases/"
)

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % Option(System.getProperty("play.version")).getOrElse("2.3.10"))

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.6.0")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.3.3")

