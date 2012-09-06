
require 'collins_client'

describe "Asset Find" do

  before :all do
    config = {:username => "blake", :password => "admin:first", :host => "http://127.0.0.1:9000"}
    @client = Collins::Client.new config
  end

  def checkQuery(params, expected_size)
    assets = @client.find params
    assets.size.should eql expected_size
  end

  #these are all pulled from prod using query logging
  
  it "attribute=POOL;FIREHOSE&attribute=PRIMARY_ROLE;SERVICE&status=Allocated&type=SERVER_NODE&details=false&operation=and:2" do
    p = {
      "pool"        => "FIREHOSE",
      "primary_role" => "SERVICE",
      "status"      => "Allocated",
      "type"        => "SERVER_NODE",
      "details"     => "false",
      "operation"   => "and"
    }
    checkQuery p, 2
  end


  it "attribute=SECONDARY_ROLE;UNDERLORD&details=false:2" do
    p = {
      "SECONDARY_ROLE" => "UNDERLORD"
    }
    checkQuery p, 2
  end

  it "attribute=HOSTNAME;service-wentworth-a2233e73.ewr01.tumblr.net&details=false:1" do
    p = {
      "hostname" => "service-wentworth-a2233e73.ewr01.tumblr.net"
    }
    checkQuery p,1
  end

  it "attribute=POOL;API_POOL&attribute=PRIMARY_ROLE;PROXY&status=Allocated&type=SERVER_NODE&details=false&operation=and:1" do
    p = {
      "pool" => "API_POOL",
      "primary_role" => "PROXY",
      "status" => "Allocated",
      "type" => "SERVER_NODE"
    }
    checkQuery p,1
  end

  it "attribute=HOSTNAME;%5EMAIL.*&attribute=POOL;MAILPARSER_POOL&attribute=PRIMARY_ROLE;TUMBLR_APP&status=Allocated&type=SERVER_NODE&details=false&operation=and:0" do
    p = {
      "hostname" => "%5EMAIL.*",
      "pool" => "MAILPARSER_POOL",
      "primary_role" => "TUMBLR_APP",
      "status" => "Allocated",
      "type" => "SERVER_NODE",
      
    }
    checkQuery p,0
  end






end
