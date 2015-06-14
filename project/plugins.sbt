resolvers ++= Seq(
    DefaultMavenRepository,
    Resolver.url("Play", url("http://download.playframework.org/ivy-releases/"))(Resolver.ivyStylePatterns),
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    "Sontype Releases" at "https://oss.sonatype.org/content/repositories/releases/"
)

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % Option(System.getProperty("play.version")).getOrElse("2.3.9"))

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.6.0")

