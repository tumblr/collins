#require 'spec_helper'
require 'collins_client'
require 'lib/collins_integration'

describe "Asset Search" do

  def checkTags query, expectedTags
    assets = @client.search query, 50
    tags = assets.map {|a| a.tag}
    tags.should include(*expectedTags)
  end

  def checkSize query, expected_size
    assets = @client.search query, 50
    assets.size.should eql expected_size
  end
    
  
  before :all do

    #putting this here means the fixtures are loaded once for all tests.  This
    #is ok for now, but if we start doing writes we have to make this execute
    #before each test
    @integration = CollinsIntegration.new 'default.yaml'
    @client = @integration.collinsClient
  end

  it "simple tag query" do
    checkTags "tag = local", ["local"]
  end

  it "hostname exact match" do
    checkTags  "hostname = web-6ec32d2e.ewr01.tumblr.net",  ["001016"]
  end

  it "hostname fuzzy match" do
    checkSize "hostname = bustworth", 11
    checkSize "hostname = \"bustworth\"", 0
  end


  it "simple or" do 
    checkTags  'hostname = web-6ec32d2e.ewr01.tumblr.net OR tag = "000981"', ["000981", "001016"]
  end

  it "simple and" do
    checkTags  'hostname = web-6ec32d2e.ewr01.tumblr.net AND tag = "001016"', [ "001016" ]
  end

  it "asset type" do
    checkSize  'type = configuration', 12
  end

  it "asset status" do
    checkSize "status = allocated AND hostname = service-*", 14
    checkSize "status = unallocated and hostname = db-*", 35

  end

  it "with type and negated status" do
    checkSize  'type = configuration AND NOT status = incomplete', 7
  end 

end
