
require 'collins_client'
require 'lib/collins_integration'

describe "Asset Find" do

  before :all do
    @integration = CollinsIntegration.new('default.yaml')
    @client = @integration.collinsClient
  end

  def checkQuery(params, expected_size)
    params[:size] = 50
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
    checkQuery p,11
  end

  it "attribute=POOL;API_POOL&attribute=PRIMARY_ROLE;PROXY&status=Allocated&type=SERVER_NODE&details=false&operation=and:1" do
    p = {
      "pool" => "API_POOL",
      "primary_role" => "PROXY",
      "status" => "Allocated",
      "type" => "SERVER_NODE",
      "operation" => "and"
    }
    checkQuery p,1
  end

  it "attribute=HOSTNAME;%5EMAIL.*&attribute=POOL;MAILPARSER_POOL&attribute=PRIMARY_ROLE;TUMBLR_APP&status=Allocated&type=SERVER_NODE&details=false&operation=or:2" do
    p = {
      "hostname" => "dev-*",
      "pool" => "DEVEL",
      "status" => "Allocated",
      "type" => "SERVER_NODE",
      "operation" => "and"
      
    }
    checkQuery p,5
  end

  it "operation=and&ASSET_TAG=&status=Allocated&state=&type=SERVER_NODE" do
    p = {
      "status" => "allocated",
      "type" => "SERVER_NODE",
      "primary_role" => "TUMBLR_APP",
      "operation" => "and"
    }
    checkQuery p,2
  end

  it "status=Allocated@primary_role=SERVICE" do
    p = {
      "primary_role" => "SERVICE",
      "status" => "Allocated",
      "operation" => "and"
    }
    checkQuery p, 14
  end

  it "status=Unallocated&type=&PRIMARY_ROLE=CACHE&POOL=MEMCACHE&MEMORY_SIZE_TOTAL=103079215104" do
    p = {
      "status" => "Unallocated",
      "primary_role" => "CACHE",
      "pool" => "MEMCACHE*",
      "memory_size_total" => "103079215104",
      "operation" => "and"
    }
    checkQuery p, 9
  end

  it "handles fuzzy hostname" do
    p = {
      "hostname" => "bustworth"
    }
    checkQuery p,11
  end

  it "handles regex" do
    p = {
      "pool" => "^MEMCACHE$"
    }
    checkQuery p,1
  end

  it "cql with other stuff" do
    p = {
      "pool" => "MEMCACHE",
      "query" => "status = allocated or (hostname = *default* AND num_disks = 2)"
    }
    checkQuery p, 5
  end

  it "strips enclosing quotes" do
    p = {
      "pool" => "MEMCACHE",
      "query" => "\"status = allocated or (hostname = *default* AND num_disks = 2)\" "
    }
    checkQuery p, 5
  end




end
