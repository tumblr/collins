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

- Setting up a configuration file

Configuration file is where consolr looks for the location of ipmitool.
If you have assets where you don't want users changing things, just add the 
asset in the dangerous assets list. Consolr will safeguard it.

Consolr authenticates with Collins through [collins-auth](https://github.com/tumblr/collins/tree/master/support/ruby/collins-auth). So 
one would want collins.yml file to be set up as well.

Configuration params are searched in these locations -- 

-- ENV['CONSOLR_CONFIG']
-- $HOME/.consolr.yml
-- /etc/consolr.yml
-- /var/db/consolr.yml

An example consolr.yml file

```    
ipmitool:  /usr/bin/ipmitool
dangerous_assets:
  - "007117"
dangerous_status:
  - "Allocated"
```

## Running the tool

```
$ consolr -a 006123 -c
```

For a full list of actions, look up the help page

```
$ consolr --help
```

## Mailing list
http://groups.google.com/group/collins-sm

## Bugs

Please report any bugs or submit a request for a patch here : <https://github.com/tumblr/collins/issues/>

## License
Copyright 2015 Tumblr Inc.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use
this file except in compliance with the License. You may obtain a copy of the
License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed
under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
CONDITIONS OF ANY KIND, either express or implied. See the License for the
specific language governing permissions and limitations under the License.
