
require 'collins_client'
require 'lib/collins_integration'

describe "Asset Find" do

  before :all do
    @integration = CollinsIntegration.new('default.yaml')
    @client = @integration.getCollinsClient
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
      "hostname" => "service-bustworth"
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
    checkQuery p,6
  end

  it "attribute=HOSTNAME;%5EMAIL.*&attribute=POOL;MAILPARSER_POOL&attribute=PRIMARY_ROLE;TUMBLR_APP&status=Allocated&type=SERVER_NODE&details=false&operation=and:0" do
    p = {
      "hostname" => "%5EMAIL.*",
      "pool" => "MAILPARSER_POOL",
      "primary_role" => "TUMBLR_APP",
      "status" => "Allocated",
      "type" => "SERVER_NODE",
      
    }
    checkQuery p,2
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
