resolvers := Seq("Tumblr Nexus Repo" at "http://repo.tumblr.net:8081/nexus/content/groups/public")

addSbtPlugin("com.twitter" % "sbt-scrooge2" % "0.0.2-SNAPSHOT")

addSbtPlugin("com.twitter" % "standard-project2" % "0.0.6-SNAPSHOT")

addSbtPlugin("com.tumblr" % "standard-project2" % "0.0.4-SNAPSHOT")

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.0.0")