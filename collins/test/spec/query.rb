#require 'spec_helper'
require 'collins_client'

describe "Asset Search" do
  
  before :all do
    config = {:username => "blake", :password => "admin:first", :host => "http://127.0.0.1:9000"}
    @client = Collins::Client.new config
  end

  it "simple tag query" do
    assets = @client.search "tag = tumblrtag1"
    assets.size.should eql 1
  end

end
