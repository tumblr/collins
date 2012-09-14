
require 'collins_client'
require 'lib/collins_integration'

describe "Asset Update and Search" do

  before :all do
    @integration = CollinsIntegration.new('default.yaml')
    @client = @integration.getCollinsClient
  end

  it "should put an asset in maintenance mode" do
    assets = @client.search 'tag = "001016" AND status= unallocated'
    assets.size.should eql 1
    @client.set_status!(assets[0], "Maintenance").should eql true
    sleep(0.2)
    assets = @client.search 'tag = "001016" AND status = maintenance'
    assets.size.should eql 1
    @client.set_status!(assets[0], "Allocated")
    sleep(0.2)
    assets = @client.search 'tag = "001016" AND status = allocated'
    assets.size.should eql 1
  end

end

