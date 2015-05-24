scalacOptions ++= Seq("-deprecation","-unchecked", "-feature")

resolvers += "Twitter Repository" at "http://maven.twttr.com/"

resolvers += "Sonatype-public" at "http://oss.sonatype.org/content/groups/public"

resolvers += "Restlet repository" at "http://maven.restlet.org"

parallelExecution in Test := false

Keys.fork in Test := true

javaOptions in Test := Seq("-Dconfig.file=conf/test.conf")

parallelExecution in IntegrationTest := false

scalaVersion := "2.10.5"

scalacOptions += "-feature"

scalacOptions += "-language:postfixOps"

scalacOptions += "-language:implicitConversions"

libraryDependencies ++= Seq(
  "org.specs2" %% "specs2" % "1.9.2" % "test",
  "com.google.guava" % "guava" % "18.0",
  "com.jolbox" % "bonecp" % "0.8.0.RELEASE",
  "nl.grons" %% "metrics-scala" % "3.5.1_a2.2",
  "com.addthis.metrics" % "reporter-config" % "2.3.1",
  "mysql" % "mysql-connector-java" % "5.1.35",
  "com.h2database" % "h2" % "1.4.187",
  "org.apache.solr" % "solr-solrj" % "5.1.0",
  "org.apache.solr" % "solr-core"  % "5.1.0",
  "org.apache.httpcomponents" % "httpclient" % "4.4.1", 
  "org.apache.httpcomponents" % "httpmime" % "4.4.1", 
  "commons-net" % "commons-net" % "3.3",
  "org.bouncycastle" % "bcprov-jdk16" % "1.46",
  "com.twitter" %% "util-core" % "6.23.0",
  "org.jsoup" % "jsoup" % "1.8.2",
  "org.squeryl" %% "squeryl" % "0.9.5-7",
  "com.gilt" %% "jerkson" % "0.6.6",
  "org.yaml" % "snakeyaml" % "1.15",
  "com.typesafe.play" %% "play-jdbc" % "2.2.6",
  "com.typesafe.play" %% "play-cache" % "2.2.6",
  "com.google.code.findbugs" % "jsr305" % "3.0.0"
)


