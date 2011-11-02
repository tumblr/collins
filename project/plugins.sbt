resolvers += "Tumblr Internal" at "http://repo.tumblr.net:8081/nexus/content/groups/public/"

resolvers += "sbt-idea-repo" at "http://mpeltonen.github.com/maven/"

addSbtPlugin("com.tumblr" %% "junit_xml_listener" % "0.5.0")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "0.11.0")