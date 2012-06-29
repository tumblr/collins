# Welcome to Serx

If you're just getting started read the included `TUTORIAL.md`.

## Overview

Provide a brief overview of Serx

## Setup

To make sure things are working properly, you may want to:

    $ ./sbt update test

This will download and configure most everything you need.

## Running

There are a few ways to start your service.  You can build a runnable
jar and tell java to run it directly:

    $ sbt package-dist
   	$ java -Dstage=development -jar ./dist/serx/serx-1.0.0-SNAPSHOT.jar

or you can ask sbt to run your service:

   	$ sbt 'run -f config/development.scala'

or you can run it via the `run` script like:

    $ ./run com.tumblr.serx.Main -f config/development.scala

`run` takes a few options, you can check via `./run -h`.

A deployed version will use the included `src/scripts/serx.sh` file.

## Configuration

The configuration allows you to customize:

 * Logging
 * Stats collection
 * Port of the admin service
 * Port of the thrift service interface
 * Port of the http service interface

For additional details on the above, check out the [ostrich](https://github.com/twitter/ostrich) documentation.

Put any other documentation about configuration here.

## Testing

Put test related notes in this section. You should create performance benchmarks. As you run them over time, be
sure to document the results here so you have a baseline as well as trends for those benchmarks. Also be sure to
include the hardware that those benchmarks were run on.

## Building a Ruby Gem for your service

A Ruby thrift client and console to your service is provided. You can build it using the following commands:

   $ ./sbt compile 
   $ gem build serx.gemspec
   $ gem install serx
   $ bundle install 

# Documenting your project, it's important

Add documentation here!  

