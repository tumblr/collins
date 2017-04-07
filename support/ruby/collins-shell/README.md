# CollinsShell Description

The collins shell is a lightweight application built on top of the
`collins_client` gem. The `collins_client` gem provides API access to collins,
the `collins_shell` application provides CLI API access to collins.

## Installation

    > gem install collins_shell

## Setup

Collins shell will look for a yaml file at ~/.collins.yaml, passed in via
`--config=my_collins.yaml`, or passed in via an environment variable like
`COLLINS=~/my_collins.yaml`. The file should look like:

    ---
    host: "http://somehost:8080"
    username: "user"
    password: "secret"

You can also specify the host, username, or options via the `--host`,
`--username` and `--password` options. If you just specify `--password`
(without a value), or your configs are missing a password, you will be
prompted for one.

## Overview

There are a few common themes found throughout the collins shell that will
help you be productive.

### Command Structure

collins-shell uses thor under the hood to handle command line dispatching and
parsing. Arguments to collins-shell consist of commands, subcommands,
arguments, and options.

Supported commands and subcommands are:

    asset
        create
        delete
        delete_attribute
        find
        get
        set_attribute
        set_status
    console
    ip_address
        allocate
        assets
        delete
        delete_all
        find
        pools
        update
    ipmi
        create
        pool
        update
    latest
    log
    logs
    power
    power_status
    provision
        host
        list
    tag
        list
        values
    version

All commands are of the form:

    collins-shell <command> <args> <options>

Every command can be described via `help`, e.g. `collins-shell help log` or
`collins-shell asset help create`. Note that to get help for a subcommand, the
help directive must come after the command, but before the subcommand.

This returns help for the `asset find` subcommand:

    collins-shell asset help find

This returns help for the `asset` command (the `find` subcommand is ignored):

    collins-shell help asset find

A command is something like `log` or `asset find`. Arguments are required
options, passed to the command without a switch (e.g. `asset get TAG`, `TAG`
is an argument there). Options are optional, and generally passed like
`--option` or `--option=value`.

### Universal Options

The following options can be specified for every command:

    --config=CONFIG       # YAML configuration file
    --debug               # Debug output
    --host=HOST           # Collins host (e.g. http://host:port)
    --quiet               # Mostly used in conjunction with commands that have an --exec option
    --timeout=SECONDS     # Seconds to allow for operation, defaults to 30
    --username=USERNAME   # Collins username
    --password=PASSWORD   # Collins password


### Common Options

Many options will allow you to specify either a selector (matching many
assets) or a tag (matching a single asset). A tag is the asset tag, a selector
is a space separated list of `key:value` pairs that are asset keys and values.
Some examples are:

    # Allocated web servers (note that multiple selectors are separated by a space)
    --selector=hostname:'^web.*' status:Allocated
    # Allocated master database servers in the main pool
    --selector=primary_role:database pool:main secondary_role:master status:Allocated
    # Asset with tag 001923
    --tag=001923

Note that any time a command will result in changing more than one asset,
collins-shell  will prompt for confirmation.

# Multi-Collins Support

Collins has a feature called 'multi-collins' that allows multiple collins servers to
know about each other. This functionality provides a unified view of all assets,
regardless of which collins server stores a given asset. If you have enabled
multi-collins, your collins-shell configuration only needs to have credentials and host
information for one of your collins servers.

By default, collins-shell only interacts a single collins server (the one specified in the configuration file). To run commands against all of your collins servers, pass the `--remote` option.

One exception to this is `asset get`. `asset get` takes `--remote=TAG` where
tag is the asset tag of the datacenter that has the asset, e.g.
`--remote=ewr01` or `--remote=d2`.

The following commands support multi-collins:

    asset find
    asset get (with --remote=DC)
    asset set_attribute
    asset delete_attribute
    asset set_status
    log

Because `asset find` supports multi-collins, for any commands that don't (e.g.
`power_status`, it's trivial to script piping the results of the find to some
other command.

# Usage

## Getting Help

Before using any command, check out the help. You can see help for a command
by running:

    collins-shell help <command>

or

    collins-shell <sub> help <command>

Where `<sub>` is something like `asset`, `ipmi` or `ip_address` and
`<command>` is something like `create`, `delete` or `get`. For example

    > collins-shell asset help get
    Usage:
      collins-shell asset get TAG
    
    Options:
      --config=CONFIG      # YAML configuration file
      --debug              # Debug output
      --host=HOST          # Collins host (e.g. http://host:port)
      --password=PASSWORD  # Collins password
      --quiet              # Be quiet when appropriate
      --timeout=N          # Collins client timeout
                           # Default: 30
      --username=USERNAME  # Collins username
      --confirm            # Require exec confirmation. Defaults to true
                           # Default: true
      --exec=EXEC          # Execute a command using the data from this asset. Use {{hostname}}, {{ipmi.password}}, etc for substitution
      --logs               # Also display asset logs
      --remote=REMOTE      # Remote location to search. This is a tag in collins corresponding to the datacenter asset
    
    get an asset and display its attributes

## Examples

    > collins-shell
    Tasks:
      collins-shell asset <command>                         # Asset related commands
      collins-shell console                                 # drop into the interactive collins shell
      collins-shell help [TASK]                             # Describe available tasks or one specific task
      collins-shell ip_address <command>                    # IP address related commands
      collins-shell ipmi <command>                          # IPMI related commands
      collins-shell log MESSAGE                             # log a message on an asset
      collins-shell power ACTION --reason=REASON --tag=TAG  # perform power action (off, on, rebootSoft, rebootHard, etc) on an asset
      collins-shell power_status --tag=TAG                  # check power status on an asset
      collins-shell provision <command>                     # Provisioning related commands
      collins-shell tag <command>                           # Tag related commands
    > collins-shell asset
    Tasks:
      collins-shell asset create --tag=TAG           # create an asset in collins
      collins-shell asset delete --tag=TAG           # delete an asset in collins (must be cancelled)
      collins-shell asset delete_attribute KEY       # delete an attribute in collins
      collins-shell asset find --selector=key value  # find assets using the specified selector
      collins-shell asset get TAG                    # get an asset and display its attributes
      collins-shell asset help [COMMAND]             # Describe subcommands or one specific subcommand
      collins-shell asset set_attribute KEY VALUE    # set an attribute in collins
      collins-shell asset set_status STATUS          # set status on an asset
    > collins-shell asset find --selector=status:Allocated 'hostname:^web.*'
    id,tag,status,type,created,updated
    18,sl-90918,Allocated,Server Node,2012-02-08T00:34:43+00:00,2012-06-09T00:31:39+00:00
    20,sl-111623,Allocated,Server Node,2012-02-08T00:34:44+00:00,2012-06-09T01:25:35+00:00
    21,sl-70108,Allocated,Server Node,2012-02-08T00:34:44+00:00,2012-06-09T00:42:16+00:00
    23,sl-89121,Allocated,Server Node,2012-02-08T00:34:47+00:00,2012-06-09T00:33:31+00:00

# Tips and Tricks

There are a couple of neat features in the collins shell to be aware of.

## Detailed asset views

I have tried to make the shell as consistent with the web UI as possible. Try
running a command like:

    collins-shell asset get VALID_TAG_HERE

The display should be familiar to people if you've logged into the web UI.

## Asset Logs

Adding `--logs` to your `asset get` or `asset find` commands will show logs
for each asset.

## Making find more useful

There are three switches that are useful to know about with the `asset find`
command.

### awk format

If you need to do some kind of post processing on data not found in the
default find results, you can specify them via the `--tags` option. This will
give you a comma separated list of tags, one for each asset. For example:

    collins-shell asset find --selector=hostname:'^web' status:Allocated --tags=hostname addresses

Will provide you a list that might look like:

    hostname,addresses
    web-7c177b48.d2.tumblr.net,10.80.96.243|10.80.96.1|255.255.248.0,192.172.29.80|192.172.29.65|255.255.255.224
    web-a1a316af.d2.tumblr.net,10.80.97.95|10.80.96.1|255.255.248.0,192.172.38.108|192.172.38.97|255.255.255.224

The header line tells you the asset tags you are displaying. Each
line has a comma separated list of values. If a value is an array of some
sort, values within it are separated by pipes. Easy parsing!

If you would prefer not to display the header line, just add `--header=false` to your find command.

Note there is one caveat, the current version of collins-shell cannot prompt for authentication
data if you are passing output to a pipe. In order to pass output to a pipe, you must have your
collins server and credentials defined in ~/.collins.yaml or pass them to collins-shell on the
command line.

### Detailed output

Try adding `--details` to your `asset find` command to get a detailed asset
view of each of the assets in the result set.

### Size

Setting `--size=N` (e.g. `--size=1000`) will give you that number of results
or less. The default is 50 results.

## Friendly Sizes

If you're querying on the `memory_size_total` or `disk_storage_total`, you can
use human readable values like `72GB` or `2.18298348411918TB`. Yeah, the disk
one kind of sucks. But, if you do a `get` or `find --details` you can see what
the correct query value is.

## Execute commands using asset data

Some commands support an `--exec` option that allows you to execute a command
using the information associated with the asset. An example might be:

    collins-shell asset get TAG \
      --exec='IPMI_PASSWORD={{ipmi.password}} ipmitool -I lanplus -E -U {{ipmi.username}} -H {{ipmi.address}} sol activate'

This will create a console session using IPMI with the asset specified by `TAG`. Any attributes of an asset
can be specified as `{{attribute}}`, e.g. `{{hostname}}` or `{{addresses.first.address}}`.

# The Interactive Shell

It is also possible to use collins shell in an interactive mode. You can drop
into the collins shell console by doing:

    collins-shell console

Be sure to provide your config YAML in the usual way. Once you are in the shell
you can get a list of global commands by typing `help`. Global commands can be
used regardless of context. Below is a sample interactive session.

    collins / > ls /
    ....
    collins / > cd /PRIMARY_ROLE
    collins /PRIMARY_ROLE > ls
    collins /PRIMARY_ROLE > cd DEVEL
    collins /PRIMARY_ROLE/DEVEL > ls --format='{{hostname}} {{status}} {{tag}}' --grep=blake
    collins /PRIMARY_ROLE/DEVEL > cd sl-91016
    collins /PRIMARY_ROLE/DEVEL/sl-91016* > ls
    collins /PRIMARY_ROLE/DEVEL/sl-91016* > cat -b
    collins /PRIMARY_ROLE/DEVEL/sl-91016* > cat /var/log/messages
    collins /PRIMARY_ROLE/DEVEL/sl-91016* > cat /var/log/NOTE
    collins /PRIMARY_ROLE/DEVEL/sl-91016* > asset.created.to_s
    collins /PRIMARY_ROLE/DEVEL/sl-91016* > power?
    collins /PRIMARY_ROLE/DEVEL/sl-91016* > reboot!
    collins /PRIMARY_ROLE/DEVEL/sl-91016* > cd ../sl-102313
    collins /PRIMARY_ROLE/DEVEL/sl-102313* > stat

This is all ruby, so you can play with a lot of this data using ruby as you
would expect. Some examples:

    collins / > ls /HOSTNAME/.*blake.* | {|array| array.map{|a| [a.tag,a.hostname,a.status]}}
    collins / > hosts = _
    collins / > hosts.select do |host|
    collins / *   host[2] == 'Allocated'
    collins / * end.map do |host|  
    collins / *   [host[0], host[1], collins_client.with_asset(host[0]).power_status]
    collins / * end  

The above checks the power status of the selected hosts.
