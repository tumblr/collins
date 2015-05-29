require 'spec_helper'

describe Collins::Ipmi do
  subject {
    asset = Collins::Asset.from_json(CollinsFixture.full_asset(true))
    asset.ipmi
  }

  it_behaves_like "flexible initializer", :id => 23, :address => "hello"

  it "#empty?" do
    subject.empty?.should be false
  end

  it "#address" do
    subject.address.should == "10.80.97.234"
  end

  it "#asset_id" do
    subject.asset_id.should == 1455
  end

  it "#gateway" do
    subject.gateway.should == "10.80.97.1"
  end

  it "#id" do
    subject.id.should == 1436
  end

  it "#netmask" do
    subject.netmask.should == "255.255.255.0"
  end

  it "#password" do
    subject.password.should == "fizzbuzz12"
  end

  it "#username" do
    subject.username.should == "root"
  end

end
