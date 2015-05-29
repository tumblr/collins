require 'spec_helper'

describe Collins::Address do
  subject {
    asset = Collins::Asset.from_json(CollinsFixture.full_asset(true))
    asset.addresses
  }

  it "#is_private?" do
    Collins::Address.is_private?("10.0.0.1").should be true
    Collins::Address.is_private?("192.168.1.1").should be true
    Collins::Address.is_private?("172.16.0.1").should be true
    Collins::Address.is_private?("172.20.0.1").should be true
    Collins::Address.is_private?("172.31.255.255").should be true
  end
  it "#is_public?" do
    Collins::Address.is_public?("4.2.2.4").should be true
    Collins::Address.is_public?("172.15.255.255").should be true
    Collins::Address.is_public?("172.32.0.1").should be true
  end

  it_behaves_like "flexible initializer", :id => 23, :address => "hello"

  it "#is_addressable?" do
    subject[0].is_addressable?.should be true
    subject[1].is_addressable?.should be true
    s = Collins::Address.new
    s.is_addressable?.should be false
  end

  it "#is_private?" do
    subject[0].is_private?.should be true
    subject[0].is_public?.should be false
  end
  it "#is_public?" do
    subject[1].is_public?.should be true
    subject[1].is_private?.should be false
  end

end
