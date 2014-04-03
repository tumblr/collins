collins_auth
============

This is a library to make it easy to obtain an authenticated collins_client object. 
It manages ENV['COLLINS_CLIENT_CONFIG'], ~/.collins.yml, /etc/collins.yml, /var/db/collins.yml, 
and user interaction to determine the right configuration and authentication tokens.

Installation
============

$ gem install collins_auth

Usage
=====

    #!/usr/bin/env ruby
    require 'collins_auth'

    # setup a client from configuration files (ENV['COLLINS_CLIENT_CONFIG'], ~/.collins.yml, /etc/collins.yml or /var/db/collins.yml)
    client = Collins::Authenticator.setup_client

    # setup a client by loading an available config like above, but prompting the user for any remaining required parameters
    client = Collins::Authenticator.setup_client prompt: true

    # setup a client by prompting the user for configuration
    client = Collins::Authenticator.setup_client prompt: :only
    
    client.find hostname: /^abc.+/

How it works
============

Only two methods are exposed:
* load_config  - loads the best configuration it can find
* setup_client - Creates a new Collins::Client from load_config output

The configuration is a merge of the following, in order of preference:
* passed in configuration
* ~/.collins.yml
* /var/db/collins.yml
* Prompt

The configuration is built up from there.  If :host is not set it is built up
from https://collins.{domain}.

Most importantly, :username and :password are considered a tuple.  If you have
full credentials in /var/db/collins.yml but only specify a username in
~/.collins.yml, the username and password from /var/db/collins.yml are used in
the configuration.

Finally, if :password or :username are not set by the end of this process, the
user is interactively asked for credentials.  This can be supressed by passing a
parameter to not prompt the user:

    client = Collins::Authenticator.setup_client({}, false)
    
License
============

Copyright 2014 Tumblr, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
