require 'spec_helper'

describe Collins::Api::Asset do

  context "#create!" do
    include_context "collins api"
    def tag; "sl-102313" end
    def method; :put end
    def uri; "/api/asset/#{tag}" end
    def with_defaults options = {}
      Hash[:generate_ipmi => false].merge(options)
    end

    it "supports generate_ipmi (true)" do
      args = {:generate_ipmi => true}
      api.with(:body => body(args)).returns 201, CollinsFixture.bare_asset(false)
      subject.create!(tag, args).should be_an_asset :tag => tag
    end

    it "supports generate_ipmi (false)" do
      args = {:generate_ipmi => false}
      api.with(:body => body(args)).returns 201, CollinsFixture.bare_asset(false)
      subject.create!(tag, args).should be_an_asset :tag => tag
    end

    it "supports status" do
      args = with_defaults(:status => 'Allocated')
      api.with(:body => body(args)).returns 201, CollinsFixture.bare_asset(false)
      subject.create!(tag, args).should be_an_asset :tag => tag, :status => 'Allocated'
    end

    it "supports type" do
      args = with_defaults(:type => 'SERVER_NODE')
      api.with(:body => body(args)).returns 201, CollinsFixture.bare_asset(false)
      subject.create!(tag, args).should be_an_asset :tag => tag, :type => 'Server Node'
    end

    it "error if response code is not 201" do
      args = with_defaults
      api.with(:body => body(args)).returns 200, CollinsFixture.bare_asset(false)
      expect { subject.create!(tag, args) }.to raise_exception(Collins::UnexpectedResponseError)
    end
  end

  context "#delete!" do
    include_context "collins api"
    def tag; "sl-102313" end
    def method; :delete end
    def uri; "/api/asset/#{tag}" end

    it "supports using a reason" do
      args = Hash[:reason => 'Angry gods']
      api.with(:body => body(args)).returns 200, CollinsFixture.status_response(false)
      subject.delete!(tag, args).should be true
    end
    it "supports not using a reason" do
      api.returns 200, CollinsFixture.status_response(false)
      subject.delete!(tag).should be true
    end
    it "raises a RequestError if the status is >= 400" do
      api.returns 409, CollinsFixture.delete_conflict
      expect { subject.delete!(tag).should }.to raise_exception(Collins::RequestError)
    end
    it "does not raise an error if !strict" do
      api.returns 409, CollinsFixture.delete_conflict
      subject(false).delete!(tag).should be false
    end
  end

  context "#exists?" do
    include_context "collins api"
    def method; :get end
    def uri; "/api/asset/sl-129278" end

    it "return false on 404" do
      api.returns 404, CollinsFixture.no_such_asset(false)
      subject(false).exists?("sl-129278").should be false
    end
    it "return false on 404 even when strict" do
      api.returns 404, CollinsFixture.no_such_asset(false)
      subject.exists?("sl-129278").should be false
    end
    it "return true on 200" do
      api.returns 200, CollinsFixture.full_asset(false)
      subject.exists?("sl-129278").should be true
    end
    it "return true on 200 when status is specified" do
      api.returns 200, CollinsFixture.full_asset(false)
      subject.exists?("sl-129278", "Allocated").should be true
    end
    it "return false on 200 when status is specified but not same" do
      api.returns 200, CollinsFixture.full_asset(false)
      subject.exists?("sl-129278", "Incomplete").should be false
    end
  end

  context "#find" do
    include_context "collins api"
    def method; :get end
    def uri; "/api/assets" end

    it "can use details" do
      api.with(:query => Hash[:details => 'true']).
        returns 200, CollinsFixture.data('find_with_details.json')
      assets = subject.find(:details => true)
      assets.length.should == 3
      assets.map{|a| a.hostname}.
        should include("dev-blake-10df20ec.d2.tumblr.net","dev-blake-f3a7c22e.d2.tumblr.net")
    end
    it "can not use details" do
      api.returns 200, CollinsFixture.data('find_no_details.json')
      assets = subject.find
      assets.length.should == 3
      assets.map{|a| a.hostname}.should == [nil,nil,nil]
    end
    it "throws an error for non-200 when not strict" do
      api.returns 201, CollinsFixture.data('find_with_details.json')
      expect { subject.find }.to raise_exception(Collins::UnexpectedResponseError)
    end
  end

  context "#count" do
    include_context "collins api"
    def method; :get end
    def uri; "/api/assets?page=0&size=1" end

    it "basic use-case" do
      api.returns 200, CollinsFixture.data('find_no_details.json')
      total = subject.count
      total.should == 3
    end
  end

  context "#get" do
    include_context "collins api"
    def method; :get end
    def uri; "/api/asset/sl-129278" end

    it "returns false if asset does not exist and not strict" do
      api.returns 500, CollinsFixture.no_such_asset(false)
      subject(false).get("sl-129278").should be false
    end

    it "throws an exception if asset does not exist and strict" do
      api.returns 500, CollinsFixture.no_such_asset(false)
      expect { subject.get("sl-129278") }.to raise_exception(Collins::RequestError)
    end

    it "returns an asset if the asset exists" do
      api.returns 200, CollinsFixture.full_asset(false)
      subject.get("sl-129278").should be_an_asset :tag => "sl-129278"
    end

    it "uses a location if provided" do
      api.with(:query => {'location' => 'test'}).returns 200, CollinsFixture.full_asset(false)
      subject.get("sl-129278", :location => "test").should be_an_asset :tag => "sl-129278"
    end

    it "discards junk params" do
      api.returns 200, CollinsFixture.full_asset(false)
      subject.get("sl-129278", :foo => 'bar').should be_an_asset :tag => 'sl-129278'
    end
  end

end
