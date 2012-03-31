scalacOptions ++= Seq("-deprecation","-unchecked")

resolvers += "Twitter Repository" at "http://maven.twttr.com/"

libraryDependencies ++= Seq(
  "org.specs2" %% "specs2" % "1.7.1" % "test",
  "org.mockito" % "mockito-all" % "1.9.0" % "test",
  "com.google.guava" % "guava" % "11.0"
)


