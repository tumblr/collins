#require 'spec_helper'
require 'collins_client'

describe "Asset Search" do

  def checkTags query, expectedTags
    assets = @client.search query, 500
    tags = assets.map {|a| a.tag}
    tags.should include(*expectedTags)
  end
    
  
  before :all do
    config = {:username => "blake", :password => "admin:first", :host => "http://127.0.0.1:9000"}
    @client = Collins::Client.new config
  end

  it "simple tag query" do
    checkTags "tag = tumblrtag1", ["tumblrtag1"]
  end

  it "hostname exact match" do
    checkTags  "hostname = web-6ec32d2e.ewr01.tumblr.net",  ["001016"]
  end

  it "simple or" do 
    checkTags  'hostname = web-6ec32d2e.ewr01.tumblr.net OR tag = "000981"', ["000981", "001016"]
  end

  it "simple and" do
    checkTags  'hostname = web-6ec32d2e.ewr01.tumblr.net AND tag = "001016"', [ "001016" ]
  end

  it "asset type" do
    checkTags  'type = configuration', [
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
    assets_1.size.should eq 19

    assets_1 = @client.search 'status = new', 1000
    assets_1.size.should eq 14
  end

  it "with type and negated status" do
    res = @client.search 'type = configuration AND NOT status = incomplete'
    checkTags  'type = configuration AND NOT status = incomplete', [
      "nodeclassifer_web_10g",
      "nodeclassifier_compute",
      "nodeclassifier_io",
      "nodeclassifier_queue",
      "nodeclassifier_web",
      "nodeclassifier_web_10g"
    ]
  end 

end
