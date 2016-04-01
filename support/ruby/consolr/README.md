```

 ▄████████  ▄██████▄  ███▄▄▄▄      ▄████████  ▄██████▄   ▄█          ▄████████ 
███    ███ ███    ███ ███▀▀▀██▄   ███    ███ ███    ███ ███         ███    ███ 
███    █▀  ███    ███ ███   ███   ███    █▀  ███    ███ ███         ███    ███ 
███        ███    ███ ███   ███   ███        ███    ███ ███        ▄███▄▄▄▄██▀ 
███        ███    ███ ███   ███ ▀███████████ ███    ███ ███       ▀▀███▀▀▀▀▀   
███    █▄  ███    ███ ███   ███          ███ ███    ███ ███       ▀███████████ 
███    ███ ███    ███ ███   ███    ▄█    ███ ███    ███ ███▌    ▄   ███    ███ 
████████▀   ▀██████▀   ▀█   █▀   ▄████████▀   ▀██████▀  █████▄▄██   ███    ███ 
                                                        ▀           ███    ███ 
                                                        
```

Consolr is a utility to communicate with Collins assets through ipmitool. Doing a
collins lookup, it retrieves the attributes necessary to invoke an ipmitool
command to the asset.
 
Consolr supports a subset of commonly used ipmitool actions making the life of 
system administrators a little easier.

## Setup 

**Consolr is only useful when you have [collins setup](http://tumblr.github.io/collins/) and a hosts fleet managed by collins with IPMI information populated for each host.**

- Make sure you have ipmitool installed.

Ubuntu

```
# apt-get install openipmi
```

Redhat Flavors

```
# yum install ipmitool
```

- Install the Consolr gem

```
# gem install consolr
```

### Configuration

The configuration file is where consolr looks for what runners you have enabled
and what parameters should be passed for them. To configure your runners add
them to the `runners` array. For any runner you have, you can add another root
key to the config object with parameters for that runner.

If you have assets where you don't want users changing things, just add the
asset in the dangerous assets list. Consolr will safeguard it.

Consolr authenticates with Collins through [collins-auth](https://github.com/tumblr/collins/tree/master/support/ruby/collins-auth). So
one would want collins.yml file to be set up as well.

Configuration params are searched in these locations:

1. `ENV['CONSOLR_CONFIG']`
2. `$HOME/.consolr.yml`
3. `/etc/consolr.yml`
4. `/var/db/consolr.yml`

An example consolr.yml file

```
runners:
  - ipmitool
  - customrunner
ipmitool: /usr/bin/ipmitool
customrunner:
  user: admin
  password: s3cr3t
dangerous_assets:
  - "007117"
dangerous_status:
  - "Allocated"
```

Consolr will load the runners listed and pick the first runner that says it can
manage the node in question by running the `can_run?` method of the runner.

## Running the tool

```
$ consolr -a 006123 -c
```

For a full list of actions, look up the help page

```
$ consolr --help
```

## Runners
Consolr ships with a runner that uses ipmitool to manage servers but there are
cases where you might want to extend it. For instance to support your favorite
IaaS provider or a private cloud. In this case, you need to write a custom
runner and deploy it. The way we do it at Tumblr is to gather our custom runner
into a gem and install that gem along with consolr. 

A runner is simply a class that extends the `Consolr::Runners::Runner` base
class and is located in the correct module. A very simple example could look
like:
```
module Consolr
  module Runners
    class MyRunner < Runner
    def initialize
      // Set up the runner
    end

    def can_run? node
      // Should return true if this runner can manage `node`
    end

    def verify node
      // Verify that the runner can talk to the node. For instance by pinging
      // the ipmi interface. 
    end

    def on node
      // issue command to power node on
    end

  end
end
```
All the methods (`on`, `off`) etc. that can be implemented can be seen in the
`Consolr::Runners::Runner` base class.

In order to package it up as a gem the directory structure should look like

```
.
└── lib
└── consolr
    └── runners
        └── myrunner.rb
```
You'll also need a gemspec file at the root.

## Mailing list
http://groups.google.com/group/collins-sm

## Bugs

Please report any bugs or submit a request for a patch here : <https://github.com/tumblr/collins/issues/>

## License
Copyright 2016 Tumblr Inc.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use
this file except in compliance with the License. You may obtain a copy of the
License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed
under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
CONDITIONS OF ANY KIND, either express or implied. See the License for the
specific language governing permissions and limitations under the License.
