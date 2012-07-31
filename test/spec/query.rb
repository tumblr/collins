#require 'spec_helper'
require 'collins_client'

describe "Asset Search" do
  
  before :all do
    config = {:username => "blake", :password => "admin:first", :host => "http://127.0.0.1:9000"}
    @client = Collins::Client.new config
  end

  it "simple tag query" do
    assets = @client.search "tag = tumblrtag1"
    assets.size.should eql 1
  end

  it "hostname exact match" do
    assets = @client.search "hostname = web-6ec32d2e.ewr01.tumblr.net"
    assets.size.should eql 1
    assets[0].tag.should eql "001016"
  end

  it "simple or" do 
    assets = @client.search 'hostname = web-6ec32d2e.ewr01.tumblr.net OR tag = "000981"'
    assets.size.should eql 2
    tags = assets.map do |asset|
      asset.tag
    end
    tags.should eql ["000981", "001016"]
  end

end
