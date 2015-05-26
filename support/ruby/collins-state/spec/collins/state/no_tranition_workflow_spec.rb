require 'spec_helper'

describe Collins::NoTransitionWorkflow do

  def process_key; "NO_TRANSITIONS_PROCESS_JSON"; end

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
    subject.manage_process("NoTransitionWorkflow")
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

    it "#start?" do
      p = get_process
      get_request = api(:get).returns(200, CollinsFixture.full_asset)

      p.expired?(tag).should be true
      p.start?(tag).should be false

      update_asset = api(:post).returns(200, CollinsFixture.status_response)
      p.start(tag).name.should == :start
      update_asset.should have_been_made.once
      get_request.should have_been_made.once
    end

    it "start triggers reprovision after expiring" do
      p = get_process
      get_request = stub_get_request(:start, time(-31, :minutes))

      p.expired?(tag).should be true
      p.start?(tag).should be true

      p_args = Hash[
        :profile => 'adminwebnode', :contact => 'crushing', :primary_role => 'TUMBLR_APP',
        :secondary_role => 'ALL', :pool => 'ADMIN_POOL'
      ]
      provision = api(:post, url("/api/provision/#{tag}"))
        .with(:body => p_args)
        .returns(200, CollinsFixture.status_response)
      logged = api(:post, url("/api/asset/#{tag}"))
        .returns(200, CollinsFixture.status_response)

      p.transition(tag).name.should == :start
      provision.should have_been_made.once
      logged.should have_been_made.once
      get_request.should have_been_made.twice
    end

    it "happy flow" do
      p = get_process
      get_request = api(:get).returns(200, CollinsFixture.full_asset)
                    .then.returns(200, json_asset_with_state(descriptor(:done, time(-1, :minute))))
      update_asset = api(:post).returns(200, CollinsFixture.status_response)

      p.expired?(tag).should be true
      p.start?(tag).should be false

      p.start(tag)
      p.vlan_moved_to_provisioning(tag)
      p.ipxe_seen(tag)
      p.kickstart_seen(tag)
      res = p.use_netcat.kickstart_started(tag)
      res.should include("attribute=#{process_key.downcase};")
      res.should =~ /kickstart_started/
      res = p.use_curl.kickstart_finished(tag)
      res.should include("attribute=#{process_key.downcase};")
      res.should =~ /kickstart_finished/
      p.use_client.vlan_move_to_production(tag)
      p.reachable_by_ip(tag)
      p.done(tag)
      p.finished?(tag).should be true
      get_request.should have_been_made.times(10)
      update_asset.should have_been_made.times(7)
    end

  end
end


