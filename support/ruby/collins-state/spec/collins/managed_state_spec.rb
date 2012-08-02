require 'spec_helper'

class ManagedStateTest
  include Collins::State::Mixin
end

class ManagedStateTestWithClient
  include Collins::State::Mixin
  attr_accessor :collins_client
  manage_state :foo do |client|
  end
  def initialize client
    @collins_client = client
  end
end

describe Collins::State do

  subject { ManagedStateTest.new }

  it "throw an exception if no collins_client" do
    expect { subject.collins_client }.to raise_error(NotImplementedError)
  end

  it "throw an exception if no logger" do
    expect { subject.logger }.to raise_error(NotImplementedError)
  end

  it "throw an exception if no transition" do
    client = double("client")
    client.should_receive(:get).with("fizz") { Collins::Asset.new("fizz") }
    msc = ManagedStateTestWithClient.new client
    expect { msc.transition("fizz") }.to raise_error(/no initial/)
  end

  it "calls super on no arg to method_missing" do
    expect { subject.foo }.to raise_error
  end

  it "calls super when arg isn't an asset" do
    expect { subject.foo(123) }.to raise_error
  end

  it "calls super when second arg not a hash" do
    expect {subject.foo("tag", 123)}.to raise_error
  end

  it "bails when no such event" do
    expect {subject.foo("tag")}.to raise_error
  end

  it "barfs if no event description" do
    expect {subject.class.event("fizz")}.to raise_error(/missing :desc/)
  end

  context "#after" do
    subject {ManagedStateTest}
    it "handles seconds" do
      subject.after(60, :seconds).should == 60
    end
    it "handles minutes" do
      subject.after(60, :minutes).should == 3600
    end
    it "handles hours" do
      subject.after(24, :hours).should == 86400
    end
  end

end
