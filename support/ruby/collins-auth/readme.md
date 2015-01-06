collins_auth
============

This is a library to make it easy to obtain an authenticated collins_client object. 
It attempts to load credentials from the following yaml files ENV['COLLINS_CLIENT_CONFIG'], ~/.collins.yml, /etc/collins.yml, /var/db/collins.yml, and supports user input.

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

License
============

Copyright 2015 Tumblr, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
