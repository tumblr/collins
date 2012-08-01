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

  it "simple and" do
    assets = @client.search 'hostname = web-6ec32d2e.ewr01.tumblr.net AND tag = "001016"'
    assets.size.should eql 1
    tags = assets.map do |asset|
      asset.tag
    end
    tags.should eql [ "001016" ] 
  end

  it "asset type" do
    assets = @client.search 'type = configuration'
    assets.size.should eq 12
    tags = assets.map do |asset|
      asset.tag
    end
    tags.should eql [
      "Tumblr",
      "dns_ewr01_tumblr_net",
      "ewr01haproxy_generation",
      "ewr01nginx_generation",
      "ewr01varnish_generation",
      "nagios_generation",
      "nodeclassifer_web_10g",
      "nodeclassifier_compute",
      "nodeclassifier_io",
      "nodeclassifier_queue",
      "nodeclassifier_web",
      "nodeclassifier_web_10g"
    ]
  end

  it "asset status" do
    assets_1 = @client.search 'status = allocated AND hostname=web-a*', 1000
    assets_1.size.should eq 37

    assets_1 = @client.search 'status = new', 1000
    assets_1.size.should eq 53
  end

  it "status and type and not" do
    assets = @client.search 'type = configuration AND NOT status = incomplete'
    assets.size.should eq 6
    tags = assets.map do |asset|
      asset.tag
    end
    tags.should eql [
      "nodeclassifer_web_10g",
      "nodeclassifier_compute",
      "nodeclassifier_io",
      "nodeclassifier_queue",
      "nodeclassifier_web",
      "nodeclassifier_web_10g"
    ]
  end 

end
