require 'spec_helper'

describe Collins::Api::Logging do

  context "#log!" do
    include_context "collins api"
    def tag; "sl-102313" end
    def method; :put end
    def uri
      "/api/asset/#{tag}/log"
    end
    def message; "hello world log"; end
    def return_struct
      OpenStruct.new("ID"=>42,"ASSET_TAG"=>tag,"CREATED"=>"2012-02-08T04:05:06", "FORMAT"=>"text/plain", "SOURCE"=>"API", "TYPE"=>"DEBUG", "MESSAGE"=>message)
    end
    
    it "supports creating a log" do
      api.returns(201, CollinsFixture.basic_log(false))
      subject.log!(tag, message).should eq(return_struct)
    end

    it "supports optional type" do
      api.returns(201, CollinsFixture.basic_log(false))
      subject.log!(tag, message, "DEBUG").should eq(return_struct)
    end

    it "error if response code is not 201" do
      api.returns(200, CollinsFixture.basic_log(false))
      expect { subject.log!(tag, message) }.to raise_exception(Collins::UnexpectedResponseError)
    end
  end

  context "#logs" do
    include_context "collins api"
    def tag; "sl-102313" end
    def method; :get end
    def uri; "/api/asset/#{tag}/logs" end
    def with_defaults options = {}
      options.merge(Hash[:page => 0, :size => 25, :sort => 'DESC'])
    end

    it "can retrive for an asset" do
      api.with(:query => with_defaults).returns(200, CollinsFixture.data('logs_no_filter.json'))
      logs = subject.logs(tag)
      logs.length.should == 3
    end
    it "can use filter" do
      query_default = with_defaults(:filter => 'DEBUG')
      api.with(:query => query_default).returns(200, CollinsFixture.data('logs_debug_filter.json'))
      logs = subject.logs(tag, {:filter => "DEBUG" })
      logs.length.should == 2
    end
    it "returns empty array when not strict and got non 2XX" do
      api.with(:query => with_defaults).returns(400, CollinsFixture.data('logs_no_filter.json'))
      subject.logs(tag).should eq []
    end
  end

  context "#all_logs" do
    include_context "collins api"
    def tag; :all_tag end
    def method; :get end
    def uri; "/api/assets/logs" end
    def with_defaults options={}
      options.merge(Hash[:page => 0, :size => 25, :sort => 'DESC'])
    end
    
    it "retrieves all logs" do
      api.with(:query => with_defaults).returns(200, CollinsFixture.data('logs_no_filter.json'))
      logs = subject.all_logs()
      logs.length.should == 3
    end
    it "can filter logs" do
      query_defaults = with_defaults({:filter => 'DEBUG'})
      api.with(:query => query_defaults).returns(200, CollinsFixture.data('logs_debug_filter.json'))
      logs = subject.all_logs({:filter => 'DEBUG'})
      logs.length.should == 2
    end
    it "#logs proxies out to all_logs if the correct arguments are passed" do
      api.with(:query => with_defaults).returns(200, CollinsFixture.data('logs_no_filter.json'))
      logs = subject.logs(tag, {tag => tag})
      logs.length.should == 3
    end
    it "returns empty array when not strict and got non 2XX" do
      api.with(:query => with_defaults).returns(400, CollinsFixture.data('logs_no_filter.json'))
      subject.all_logs().should eq []
    end
  end

  context "#search_logs" do
    include_context "collins api"
    def method; :get end
    def uri; "/api/assets/logs/search" end
    def with_defaults options={}
      options.merge(Hash[:page => 0, :size => 25, :sort => 'DESC', :sortField => 'ID'])
    end

    it "retrieves all logs by default" do
      api.with(:query => with_defaults).returns(200, CollinsFixture.data('logs_no_filter.json'))
      logs = subject.search_logs
      logs.length.should == 3
    end
    it "retrieves filtered logs" do
      query_with = with_defaults({:query => 'DEBUG'})
      api.with(:query => query_with).returns(200, CollinsFixture.data('logs_debug_filter.json'))
      logs = subject.search_logs({:query => 'DEBUG'})
      logs.length.should == 2
    end
    it "can sort differently" do
      query_with = with_defaults({:sortField => 'SEVERITY'})
      api.with(:query => query_with).returns(200, CollinsFixture.data('logs_no_filter.json'))
      logs = subject.search_logs({:sortFild => 'SEVERITY'})
      logs.length.should == 3
    end
    it "returns empty array when not strict and got non 2XX" do
      api.with(:query => with_defaults).returns(400, CollinsFixture.data('logs_no_filter.json'))
      subject.search_logs().should eq []
    end
  end
  context "#get_log" do
    include_context "collins api"
    def method; :get end
    def id; 42 end
    def uri; "/api/log/#{id}" end
    def return_struct
      OpenStruct.new("ID"=>42,"ASSET_TAG"=>"sl-102313","CREATED"=>"2012-02-08T04:05:06", "FORMAT"=>"text/plain", "SOURCE"=>"API", "TYPE"=>"DEBUG", "MESSAGE"=>"hello world log")
    end

    it "retrieves log by id" do
      api.returns(200, CollinsFixture.basic_log(false))
      subject.get_log(id).should eq return_struct
    end

    it "returns nil if not strict and got non 2XX" do
      api.returns(400, CollinsFixture.basic_log(false))
      subject.get_log(id).should eq nil
    end
  end
end
