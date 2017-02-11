# Collins

## Overview

Published [documentation](https://tumblr.github.io/collins) follows the most [recent release](https://tumblr.github.io/collins/downloads.html) of collins.

For documentation on developing, building, general architecture and unreleased changes **please use the [wiki](https://github.com/tumblr/collins/wiki)**. This documentation is in flux, currently lacking and constantly being worked on.

All documentation links point to the most recent release.

[![Build Status](https://travis-ci.org/tumblr/collins.png?branch=master)](https://travis-ci.org/tumblr/collins)
[![Dependency Status](https://www.versioneye.com/user/projects/555e7598393564000d040000/badge.svg?style=flat)](https://www.versioneye.com/user/projects/555e7598393564000d040000)

## Quickstart

[Docker](https://tumblr.github.io/collins/#quickstart-docker)

[Use a Zip](https://tumblr.github.io/collins/#quickstart-zip)

[Build from Source](https://tumblr.github.io/collins/#quickstart-source)

## Docker Image

Details about the docker container and building your own container are available in the [documentation](http://tumblr.github.io/collins/index.html#docker)

tl;dr: ```docker run -p 9000:9000 tumblr/collins```

## License

Copyright 2016 Tumblr, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

## Support/Questions

Email collins-sm@googlegroups.com or see the mailing list archive at https://groups.google.com/forum/#!forum/collins-sm

## Internal (Tumblr) docs

To create a production zip and deploy to production:

  - Check Collins Runbook on internal wiki for environment specific considerations
  - ensure capistrano 2.15.5 is installed
  - ensure net-ssh < 2.7.0 is installed - versions 2.7.0 and above are broken with our environment
  - download the minimal package for play activator 1.3.6 from [here](https://downloads.typesafe.com/typesafe-activator/1.3.6/typesafe-activator-1.3.6-minimal.zip). Unpackage it into ~/src/activator-1.3.6-minimal or define $PLAY_CMD with an alternate location
  - run `./scripts/package.sh` which will produce `target/collins.zip`
  - run `cap publish:collins` which will upload and link to release to `http://repo.tumblr.net:8888/collins.zip`
  - run `cap ewr01 deploy` to deploy to ewr01 and `cap d2 deploy` to deploy to d2
