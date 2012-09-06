scalacOptions ++= Seq("-deprecation","-unchecked")

resolvers += "Twitter Repository" at "http://maven.twttr.com/"

parallelExecution in Test := false

parallelExecution in IntegrationTest := false

libraryDependencies ++= Seq(
  "org.specs2" %% "specs2" % "1.7.1" % "test",
  "org.mockito" % "mockito-all" % "1.9.0" % "test",
  "com.google.guava" % "guava" % "11.0.2",
  "com.yammer.metrics" %% "metrics-scala" % "2.1.1",
  "mysql" % "mysql-connector-java" % "5.1.19",
  "com.h2database" % "h2" % "1.3.158",
  "org.apache.solr" % "solr-solrj" % "3.6.1",
  "org.apache.solr" % "solr-core"  % "3.6.1",
  "org.apache.httpcomponents" % "httpclient" % "4.2.1", 
  "org.apache.httpcomponents" % "httpmime" % "4.2.1", 
  "javax.servlet" % "servlet-api" % "2.5",
  "commons-net" % "commons-net" % "3.1",
  "org.apache.commons" % "commons-lang3" % "3.1",
  "org.bouncycastle" % "bcprov-jdk16" % "1.46",
  "com.twitter" %% "util-core" % "1.12.12",
  "com.twitter" %% "finagle-http" % "1.10.0",
  "org.jsoup" % "jsoup" % "1.6.1",
  "org.squeryl" %% "squeryl" % "0.9.5",
  "org.yaml" % "snakeyaml" % "1.11-SNAPSHOT" from ("file://" + file(".") + "lib/snakeyaml-1.11-SNAPSHOT.jar")
)


