
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
      "pool"        : "FIREHOSE",
      "primaryRole" : "SERVICE",
      "status"      : "Allocated",
      "type"        : "SERVER_NODE",
      "details"     : "false",
      "operation"   : "and"
    }
    checkQuery p 2
  end
end
