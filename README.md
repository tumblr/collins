# Collins

## Overview

There is good documentation available at http://tumblr.github.com/collins. The
documentation below is for developers or people setting up a new instance.

The most recent production build of collins is available [here](http://tumblr.github.com/collins/downloads.html)

## Quickstart - Screencast

http://www.youtube.com/watch?v=rEyoS-iofK8

## Quickstart - Building Collins

http://tumblr.github.com/collins/#quickstart

## Collins for Production

http://tumblr.github.com/collins/#quickstart-prod

## License

Copyright 2012 Tumblr, Inc.

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
  - ensure capistrano 2.15.5 is installed
  - download and unpackage play @ ~/src/play-2.0.3 or define $PLAY_CMD with an alternate location
  - run `./scripts/package.sh` which will produce `target/collins.zip`
  - run `cap publish:collins` which will upload and link to release to `http://repo.tumblr.net:8888/collins.zip`
  - run `cap ewr01 deploy` to deploy to ewr01 and `cap d2 deploy` to deploy to d2