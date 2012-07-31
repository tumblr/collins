
require 'collins_client'

describe "Asset Update and Search" do

  def foo
  end
  
  before :all do
    config = {:username => "blake", :password => "admin:first", :host => "http://127.0.0.1:9000"}
    @client = Collins::Client.new config
  end

  it "put asset in maintenance mode" do
    assets = @client.search 'tag = "001016"'
    assets.size.should eql 1
    #the asset should be in allocated mode, but might not if a previous test failed
    @client.set_status!(assets[0], "Allocated").should eql true
    @client.set_status!(assets[0], "Maintenance").should eql true
    sleep(0.2)
    assets = @client.search 'tag = "001016" AND status = maintenance'
    assets.size.should eql 1
    @client.set_status!(assets[0], "Allocated")
    sleep(0.2)
    assets = @client.search 'tag = "001016" AND status = allocated'
    assets.size.should eql 1
  end

  it "create and delete asset" do
    newtag = "ceate_test_%s" % Random.rand(1000000)
    @client.create!(newtag)
    sleep(0.2)
    assets = @client.search ("tag = %s" % newtag)
    assets.size.should eql 1
    @client.set_status!(newtag, "decommissioned").should eql true
    sleep(0.2)
    @client.delete!(newtag).should eql true
    assets = @client.search ("tag = %s" % newtag)
    assets.size.should eql 0
  end

end

