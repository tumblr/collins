

require 'collins_client'
require 'lib/collins_integration'

describe "Asset Find" do

  before :all do
    @integration = CollinsIntegration.new('default.yaml')
    @client = @integration.collinsClient
  end

  it "get some logs" do
    @client.search_logs({:query => "SELECT asset_log WHERE asset_tag = 000918 AND severity = WARNING"}).size.should eql 2
  end

  it "get some more logs!" do
    @client.search_logs({:query => "SELECT asset_log WHERE asset_tag = 000918 AND (severity = NOTE or severity = notice)"}).size.should eql 5
  end

  it "puts the logs in the index" do
    @client.log! "000918", "or else it gets the hose again", Collins::Api::Logging::Severity::WARNING
    @client.search_logs({:query => "SELECT asset_log WHERE asset_tag = 000918 AND severity = WARNING"}).size.should eql 3
  end

end
