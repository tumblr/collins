$:.unshift File.join File.dirname(__FILE__), *%w[.. .. support ruby collins-client lib]
require 'collins_client'

HTTP_DEBUG=true

describe "Asset State Management" do

  before :all do
    config = {:username => "blake", :password => "admin:first", :host => "http://127.0.0.1:9000", :strict => true, :trace => false}
    @client = Collins::Client.new config
    begin
      @tag = "testtag1"
      @client.create! @tag, :status => "New", :type => "SERVER_NODE"
      @client.state_create! "TESTSTATE1", "Test Label", "Test Description", "Maintenance"
    rescue
    end
    @client.set_status! @tag, "New", "Doing Stuff", "New"
  end

  it "200 with just status" do
    asset = @client.get @tag
    asset.status.should === "New"
    res = @client.set_status! @tag, :status => "Maintenance"
    res.should be_true
    asset = @client.get @tag
    asset.status.should === "Maintenance"
  end

  it "200 with just state" do
    res = @client.set_status! @tag, :state => "Running"
    res.should be_true
    asset = @client.get @tag
    asset.state.name.should === "RUNNING"
  end

  it "200 with status and state" do
    res = @client.set_status! @tag, :status => "Maintenance", :state => "TESTSTATE1"
    res.should be_true
    asset = @client.get @tag
    asset.status.should === "Maintenance"
    asset.state.name.should === "TESTSTATE1"
  end

  it "400 with invalid status" do
    expect {
      @client.set_status! @tag, :status => "Poop"
    }.to raise_error { |err|
      err.should be_a(Collins::RequestError)
      err.code.should === 400
      err.message.should =~ /status/
    }
  end

  it "400 with invalid state" do
    expect {
      @client.set_status! @tag, :state => "Poop"
    }.to raise_error { |err|
      err.should be_a(Collins::RequestError)
      err.code.should === 400
      err.message.should =~ /state/
    }
  end

  it "400 with missing status and missing state" do
    expect {
      @client.set_status! @tag, :fizz => "buzz"
    }.to raise_error { |err|
      err.should be_a(Collins::RequestError)
      err.code.should === 400
      err.message.should =~ /neither/
    }
  end

  it "400 with missing reason" do
    expect {
      @client.set_status! @tag, :status => "Incomplete", :reason => nil
    }.to raise_error { |err|
      err.should be_a(Collins::RequestError)
      err.code.should === 400
      err.message.should =~ /reason/
    }
  end

  it "409 with incompatible status" do
    expect {
      @client.set_status! @tag, :status => "Incomplete"
    }.to raise_error { |err|
      err.should be_a(Collins::RequestError)
      err.code.should === 409
      err.message.should =~ /State 'TESTSTATE1' can not be used with status 'Incomplete'/
    }
  end

  it "409 with incompatible state" do
    expect {
      @client.set_status!(@tag, :status => "Unallocated", :state => "RUNNING").should be_true
      @client.get(@tag).status.should === "Unallocated"
      @client.set_status!(@tag, :state => "TESTSTATE1")
    }.to raise_error { |err|
      err.should be_a(Collins::RequestError)
      err.code.should === 409
      err.message.should =~ /State 'TESTSTATE1' can not be used with status 'Unallocated'/
    }
  end

  it "409 with incompatible state and status" do
    expect {
      @client.set_status!(@tag, :status => "Maintenance", :state => "NEW").should be_true
      @client.get(@tag).status.should === "Maintenance"
      @client.set_status!(@tag, :status => "New", :state => "TESTSTATE1")
    }.to raise_error { |err|
      err.should be_a(Collins::RequestError)
      err.code.should === 409
      err.message.should =~ /State 'TESTSTATE1' can not be used with status 'New'/
    }
  end

end
