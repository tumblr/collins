$:.unshift File.join File.dirname(__FILE__), *%w[.. .. support ruby collins-client lib]
require 'collins_client'
require 'lib/collins_integration'

HTTP_DEBUG=true

describe "Asset State Management" do

  before :all do
    @integration = CollinsIntegration.new('default.yaml')
    @client = @integration.collinsClient
  end

  it "get_all" do
    states = @client.state_get_all
    states.should have_at_least(13).items
    states.each do |state|
      state.name.should_not be_empty
      state.label.should_not be_empty
      state.description.should_not be_empty
    end
  end

  context "create" do
    before(:all) {
      ["TESTING1", "TESTING2", "Testable"].each do |state|
        begin
          @client.state_delete! state
        rescue
        end
      end
    }
    before(:each) {
      Collins::Api::AssetState.state_test = false
    }
    def verify_create name, label, description, status = nil
      res = @client.state_create! name, label, description, status
      res.should === true
      state = @client.state_get name
      state.name.should === name
      state.label.should === label
      state.description.should === description
      if status then
        state.status.name.should === status
      end
    end
    def verify_error name, label, description, message, code = 400, status = nil
      Collins::Api::AssetState.state_test = true
      expect {
        @client.state_create! name, label, description, status
      }.to raise_error { |err|
        err.should be_a(Collins::RequestError)
        err.code.should === code
        err.message.should =~ /#{message}/
      }
    end
    it "201 response on success" do
      verify_create "TESTING1", "Testing Label", "Testing Description"
    end
    it "201 response with status" do
      verify_create "TESTING2", "Testing Label", "Testing Description", "Maintenance"
    end
    it "400 if name too short" do
      verify_error "1", "Test", "Test", "name"
    end
    it "400 if name too long" do
      verify_error "123456789012345678901234567890123", "Test", "Test", "name"
    end
    it "400 if status too short" do
      verify_error "Testable", "Test", "Test", "status", 400, "1"
    end
    it "400 if status invalid" do
      verify_error "Testable", "Test", "Test", "status", 400, "Poop"
    end
    it "400 if label too short" do
      verify_error "Testable", "T", "Test", "label"
    end
    it "400 if label too long" do
      verify_error "Testable", "123456789012345678901234567890123", "Test", "label"
    end
    it "400 if description too short" do
      verify_error "Testable", "Test", "T", "description"
    end
    it "400 if description too long" do
      verify_error "Testable", "Test", ("T"*256), "description"
    end
    it "409 if duplicate name" do
      verify_error "RUNNING", "Test", "Test", "name", 409
    end
  end

  context "update" do
    before(:all) {
      ["TESTINGU", "TESTINGM", "FOO3"].each do |name|
        begin
          @client.state_delete! name
        rescue
        end
      end
      @client.state_create! "TESTINGU", "Test Label", "Test Description"
      @client.state_create! "TESTINGM", "Test Label", "Test Description"
    }
    before(:each) {
      Collins::Api::AssetState.state_test = false
    }
    def verify_error message, code, options
      Collins::Api::AssetState.state_test = true
      old_name = options.delete(:old_name) || "TESTINGU"
      expect {
        @client.state_update! old_name, options
      }.to raise_error { |err|
        err.should be_a(Collins::RequestError)
        err.code.should === code
        err.message.should =~ /#{message}/
      }
    end
    def verify_update old_name, options
      res = @client.state_update! old_name, options
      res.should be_true
      name_to_fetch = options.key?(:name) ? options[:name] : old_name
      state = @client.state_get name_to_fetch
      state.empty?.should be_false
      state.name.should === name_to_fetch
      options.each do |k,v|
        if k == :status then
          name = state.status.name
          if v == "Any" then
            name.should be_nil
          else
            name.should === v
          end
        else
          state.send(k).should === v
        end
      end
    end
    it "modifies names" do
      verify_update "TESTINGM", :name => "FOO3"
    end
    it "modifies labels" do
      verify_update "FOO3", :label => "Foo Label"
    end
    it "modifies descriptions" do
      verify_update "FOO3", :description => "Foo Description"
    end
    it "modifies status to any status" do
      verify_update "FOO3", :status => "Any"
    end
    it "modifies status to fixed status" do
      verify_update "FOO3", :status => "Maintenance"
    end
    it "400 if name too short" do
      verify_error "name", 400, :name => "1"
    end
    it "400 if name too long" do
      verify_error "name", 400, :name => "123456789012345678901234567890123"
    end
    it "400 if status too short" do
      verify_error "status", 400, :status => "1"
    end
    it "400 if status invalid" do
      verify_error "status", 400, :status => "Poop"
    end
    it "400 if label too short" do
      verify_error "label", 400, :label => "l" 
    end
    it "400 if label too long" do
      verify_error "label", 400, :label => "123456789012345678901234567890123"
    end
    it "400 if description too short" do
      verify_error "description", 400, :description => "D"
    end
    it "400 if description too long" do
      verify_error "description", 400, :description => ("D"*256)
    end
    it "404 on invalid name" do
      verify_error "name", 404, :old_name => "FIZZBUZZ", :label => "Label"
    end
    it "409 on system name" do
      verify_error "name", 409, :old_name => "RUNNING", :label => "Label"
    end
    it "409 on name in use" do
      verify_error "name", 409, :name => "TESTINGU"
    end
  end

  context "delete" do
    before(:all) {
      begin
        @client.state_delete! "TESTINGD"
      rescue
      end
      @client.state_create! "TESTINGD", "Test Label", "Test Description"
    }
    before(:each) {
      Collins::Api::AssetState.state_test = false
    }
    def verify_error name, message, code
      Collins::Api::AssetState.state_test = true
      expect {
        @client.state_delete! name
      }.to raise_error { |err|
        err.should be_a(Collins::RequestError)
        err.code.should === code
        err.message.should =~ /#{message}/
      }
    end
    it "1 on success" do
      @client.state_delete!("TESTINGD").should === 1
    end
    it "400 on invalid name" do
      verify_error "F", "name", 400
    end
    it "404 on no such name" do
      verify_error "FIZZBUZZ", "name", 404
    end
    it "409 on system name" do
      verify_error "RUNNING", "name", 409
    end
  end

  context "get" do
    before(:each) {
      Collins::Api::AssetState.state_test = false
    }
    it "200 if state exists" do
      res = @client.state_get "RUNNING"
      res.should be_a(Collins::AssetState)
      res.name.should === "RUNNING"
    end
    it "404 and empty" do
      res = @client.state_get "FIZZBUZZ"
      res.empty?.should be_true
    end
  end


end
