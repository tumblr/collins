
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
    checkQuery p, 1
  end


  it "attribute=HOSTNAME;service-wentworth&details=false:1" do
    p = {
      "hostname" => "service-bustworth*"
    }
    checkQuery p,10
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
      "hostname" => "dev-*",
      "pool" => "DEVEL",
      "status" => "Allocated",
      "type" => "SERVER_NODE",
      
    }
    checkQuery p,5
  end

  it "operation=and&ASSET_TAG=&status=Allocated&state=&type=SERVER_NODE" do
    p = {
      "status" => "allocated",
      "type" => "SERVER_NODE",
      "primary_role" => "TUMBLR_APP"
    }
    checkQuery p,2
  end






end
