# Indefatigable 

A Scala collectd plugin for aggregating App-level statistics and periodically
forwarding them to an OpenTSDB installation.


### Packaging
Proguard is used to package up Indefatigable, its dependencies, and the scala runtime into a single Jar:

    sbt proguard