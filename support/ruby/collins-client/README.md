# Collins::Client

The collins-service gem provides a library for API access to Collins.

## Installation

First install rvm

    $ bash -s stable < <(curl -s https://raw.github.com/wayneeseguin/rvm/master/binscripts/rvm-installer)
    $ source ~/.bash_profile
    $ rvm requirements
    $ rvm install 1.9.3
    $ rvm use 1.9.3

Install the collins gem and use it

    $ gem install collins_client

Remember, if you don't have 1.9.3 set as the default, before you use collins you'll need to do `rvm use 1.9.3`.

## Usage

    #!/usr/bin/env ruby
    require 'collins_client'
    config = {:username => "foo", :password => "bar", :host => "http://127.0.0.1:8080"}
    client = Collins::Client.new config
    client.find :HOSTNAME => /^abc.*/

## Note for developers

If you are implementing support for a new API endpoint, and that endpoint
requires an asset tag, please observe the standard of having the method
parameter be named `asset_or_tag`. For instance do:

    def new_method asset_or_tag
      # some work
    end

and not

    def new_method an_asset
      # some work
    end

The `AssetClient` class depends on this naming convention to know how to
appropriately proxy method calls to the collins client instance.
