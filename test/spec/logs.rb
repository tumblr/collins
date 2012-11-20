

require 'collins_client'
require 'lib/collins_integration'

describe "Asset Find" do

  def checkOrder(params, id_list)
    params[:size] = 50
    assets = @client.search_logs params
    assets.map{|x| x.ID}.should eql id_list
  end

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

  it "sorts the logs!" do
    # NOTE - these tests might "fail" because two of the logs are the same time
    # (34587, 34589) and might appear out of oder
    p = {
      :query => "SELECT asset_log WHERE asset_tag = 000918 AND (severity = NOTE or severity = notice)",
      :sortField => "CREATED",
      :sort => "ASC"
    }
    checkOrder(p, [34587, 34589, 34726, 34727, 138243])
    p = {
      :query => "SELECT asset_log WHERE asset_tag = 000918 AND (severity = NOTE or severity = notice)",
      :sortField => "CREATED",
      :sort => "DESC"
    }
    checkOrder(p, [34589, 34587, 34726, 34727, 138243].reverse)
  end

  it "puts the logs in the index" do
    @client.log! "000918", "or else it gets the hose again", Collins::Api::Logging::Severity::WARNING
    @client.search_logs({:query => "SELECT asset_log WHERE asset_tag = 000918 AND severity = WARNING"}).size.should eql 3
  end

end

