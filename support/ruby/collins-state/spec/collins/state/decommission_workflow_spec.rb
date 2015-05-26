require 'spec_helper'

describe Collins::DecommissionWorkflow do

  def process_key; "DECOMMISSION_PROCESS_JSON"; end

  def descriptor name, timestamp = false, extras = {}
    if timestamp == false then
      timestamp = Time.now.utc.to_i - 60
    end
    desc = 'fizz buzz'
    s = Collins::State::Specification.new name, desc, timestamp
    extras.each {|k,v| s[k] = v}
    s
  end
  def json_asset_with_state state
    asset = CollinsFixture.full_asset(true)
    asset["data"]["ATTRIBS"]["0"][process_key] = state.to_json
    JSON.dump(asset)
  end
  def time int, by
    case by
    when :hours, :hour
      Time.now.utc + (int * 60 * 60)
    when :minutes, :minute
      Time.now.utc + (int * 60)
    else
      Time.now.utc + int
    end
  end
  def get_process
    subject.manage_process("DecommissionWorkflow")
  end

  context "specification" do
    it "to_json/from_json" do
      spec = Collins::State::Specification.new "spec name", "specification description then", 0
      json_s = JSON.dump(spec)
      spec_from_json = JSON.parse(json_s, :create_additions => true)
      spec.should == spec_from_json
    end
  end

  context "predicate methods" do
    include_context "collins api"
    def tag; "sl-129278"; end
    def method; :get; end
    def uri; "/api/asset/#{tag}"; end

    it "true when specified and same" do
      p = get_process
      asset = json_asset_with_state descriptor(:start, Time.now)
      api.returns 200, asset
      p.start?(tag).should be true
      p.respond_to?(:start).should be true
      p.respond_to?(:start?).should be true
      p.respond_to?(:slkj).should be false
      p.finished?(tag).should be false
      p.power_on_for_cleaning?(tag).should be false
    end

    it "false when does not exist" do
      p = get_process
      p.fizz?("thing").should be false
    end

    it "false when specified and different" do
      p = get_process
      asset = json_asset_with_state descriptor(:power_on_for_cleaning, time(5, :hours))
      api.returns 200, asset
      p.start?(tag).should be false
      p.power_on_for_cleaning?(tag).should be true
      p.power_off_after_cleaning?(tag).should be false
    end
    it "false when unspecified" do
      p = get_process
      asset = CollinsFixture.full_asset
      api.returns 200, asset
      p.start?(tag).should be false
    end
  end

  context "expiration" do
    include_context "collins api"
    def tag; "sl-129278"; end
    def method; :get; end
    def uri; "/api/asset/#{tag}"; end

    it "true when timestamp + expiration < now" do
      p = get_process
      asset = json_asset_with_state descriptor(:start, time(-36, :hours))
      api.returns 200, asset
      p.expired?(tag).should be true
    end
    it "false when timestamp + expiration >= now" do
      p = get_process
      asset = json_asset_with_state descriptor(:start, time(5, :minutes))
      api.returns 200, asset
      p.expired?(tag).should be false
    end
    it "handle very old timestamps" do
      p = get_process
      asset = json_asset_with_state descriptor(:start, Time.at(0))
      api.returns 200, asset
      p.expired?(tag).should be true
    end
  end

  context "basic workflow" do
    include_context "collins api"
    def tag; "sl-129278"; end
    def uri; "/api/asset/#{tag}"; end
    def stub_get_request state, expires, extras = {}
      asset = json_asset_with_state descriptor(state, expires, extras)
      api(:get).returns(200, asset)
    end
    def response_with_attempts state, expires, attempt_count = 1
      attempts = (0...attempt_count).map do |id|
        Hash[:count => id, :timestamp => time(-(attempt_count - id), :hours)]
      end
      extras = {:attempts => attempts}
      json_asset_with_state descriptor(state, expires, extras)
    end

    it "start transition failed" do
      p = get_process
      get_request = stub_get_request(:start, time(-36, :hours))
                    .then.returns(200, response_with_attempts(:start, time(-36, :hours), 1))
                    .then.returns(200, response_with_attempts(:start, time(-36, :hours), 2))
                    .then.returns(200, response_with_attempts(:start, time(-36, :hours), 3))

      p.expired?(tag).should be true

      power_off = api(:post, url("#{uri}/power"))
        .with(:body => body(:action => "powerOff"))
        .returns(200, CollinsFixture.status_response_false)
      update_asset = api(:post).returns(200, CollinsFixture.status_response)

      res = p.transition(tag, :quiet => true)
      res.name.should == :start
      res[:attempts].size.should == 1
      res = p.transition(tag, :quiet => true)
      res.name.should == :start
      res[:attempts].size.should == 2
      expect { p.transition(tag) }.to raise_error(Collins::CollinsError)
      res = p.transition(tag, :quiet => true)
      res.name.should == :start
      res[:attempts].size.should == 4

      get_request.should have_been_made.times(4)
      power_off.should have_been_made.times(4)
      update_asset.should have_been_made.times(4)
    end

    it "not expired does nothing" do
      p = get_process
      get_request = stub_get_request(:start, time(-1, :hours))

      p.expired?(tag).should be false
      p.transition(tag).name == :start
      get_request.should have_been_made.once
    end

    it "transition -> start" do
      p = get_process
      get_request = api(:get).returns(200, CollinsFixture.full_asset)

      p.expired?(tag).should be true
      get_request.should have_been_made.once

      update_asset = api(:post).returns(200, CollinsFixture.status_response)

      p.transition(tag).name.should == :start

      update_asset.should have_been_made.once
    end

    it "start -> initial_power_off" do
      p = get_process
      get_request = stub_get_request(:start, time(-36, :hours))

      p.expired?(tag).should be true
      get_request.should have_been_made.once

      power_off = api(:post, url("#{uri}/power"))
        .with(:body => body(:action => "powerOff"))
        .returns(200, CollinsFixture.status_response)
      update_asset = api(:post).returns(200, CollinsFixture.status_response)

      p.transition(tag).name.should == :initial_power_off

      power_off.should have_been_made.once
      update_asset.should have_been_made.once
    end


    it "initial_power_off -> power_on_for_cleaning" do
      p = get_process

      get_request = stub_get_request(:initial_power_off, time(-26, :hours))
      p.expired?(tag).should be true
      get_request.should have_been_made.once

      power_on = api(:post, url("#{uri}/power"))
        .with(:body => body(:action => "powerOn"))
        .returns(200, CollinsFixture.status_response)
      update_asset = api(:post).returns(200, CollinsFixture.status_response)

      p.transition(tag).name.should == :power_on_for_cleaning
      power_on.should have_been_made.once
      update_asset.should have_been_made.once
    end

    it "power_on_for_cleaning -> provision_for_cleaning" do
      p = get_process

      get_request = stub_get_request(:power_on_for_cleaning, time(-3, :hours))
      p.expired?(tag).should be true
      get_request.should have_been_made.once

      provision = api(:post, url("/api/provision/#{tag}"))
        .with(:body => body(:profile => 'destroy', :contact => 'blake'))
        .returns(200, CollinsFixture.status_response)
      update_asset = api(:post).returns(200, CollinsFixture.status_response)

      p.transition(tag).name.should == :provision_for_cleaning
      provision.should have_been_made.once
      update_asset.should have_been_made.once
    end

    it "provision_for_cleaning -> power_off_after_cleaning -> done" do
      p = get_process

      get_request = stub_get_request(:provision_for_cleaning, time(-73, :hours))
                    .then.returns(200, json_asset_with_state(descriptor(:power_off_after_cleaning, time(-1, :minute))))
                    .then.returns(200, json_asset_with_state(descriptor(:done, time(-1, :minute))))

      p.expired?(tag).should be true

      power_off = api(:post, url("#{uri}/power"))
        .with(:body => body(:action => 'powerOff'))
        .returns(200, CollinsFixture.status_response)
      update_asset = api(:post).returns(200, CollinsFixture.status_response)

      p.transition(tag).name.should == :done
      p.finished?(tag).should be true
      get_request.should have_been_made.times(3)
      p.done?(tag).should be true
      power_off.should have_been_made.once
      update_asset.should have_been_made.twice
    end

    it "done -> done" do
      p = get_process

      get_request = stub_get_request(:done, time(-1, :hour))
      del_req = api(:delete, url("#{uri}/attribute/#{process_key.downcase}")).returns(202, CollinsFixture.status_response)

      p.expired?(tag).should be true
      get_request.should have_been_made.once

      p.transition(tag).name.should == :done
      p.finished?(tag).should be true
      p.done?(tag).should be true
      p.reset!(tag).should be true
      del_req.should have_been_made.once
    end
  end
end
