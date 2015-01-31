scalacOptions ++= Seq("-deprecation","-unchecked")

resolvers += "Twitter Repository" at "http://maven.twttr.com/"

resolvers += "Sonatype-public" at "http://oss.sonatype.org/content/groups/public"

parallelExecution in Test := false

parallelExecution in IntegrationTest := false

scalaVersion := "2.10.3"

scalacOptions += "-feature"

scalacOptions += "-language:postfixOps"

scalacOptions += "-language:implicitConversions"

libraryDependencies ++= Seq(
  "org.specs2" %% "specs2" % "1.7.1" % "test",
  "com.google.guava" % "guava" % "15.0",
  "com.jolbox" % "bonecp" % "0.8.0.RELEASE",
  "nl.grons" %% "metrics-scala" % "2.2.0",
  "com.addthis.metrics" % "reporter-config" % "2.1.0",
  "mysql" % "mysql-connector-java" % "5.1.19",
  "com.h2database" % "h2" % "1.3.158",
  "org.apache.solr" % "solr-solrj" % "3.6.1",
  "org.apache.solr" % "solr-core"  % "3.6.1",
  "org.apache.httpcomponents" % "httpclient" % "4.2.1", 
  "org.apache.httpcomponents" % "httpmime" % "4.2.1", 
  "javax.servlet" % "servlet-api" % "2.5",
  "commons-net" % "commons-net" % "3.1",
  "org.bouncycastle" % "bcprov-jdk16" % "1.46",
  "com.twitter" %% "util-core" % "6.19.0",
  "com.twitter" %% "finagle-http" % "6.20.0",
  "org.jsoup" % "jsoup" % "1.6.1",
  "org.squeryl" %% "squeryl" % "0.9.5-7",
  "com.gilt" %% "jerkson" % "0.6.6",
  "play" %% "play-jdbc" % "2.1.5"
)


