require 'spec_helper'

describe Collins::Asset do

  context "initialized assets should have" do
    subject { Collins::Asset.from_json(CollinsFixture.bare_asset(true), true)}
    it "#tag" do
      subject.tag.should == "sl-102313"
    end
    it "#status" do
      subject.status.should == "Allocated"
    end
    it "#type" do
      subject.type.should == "Server Node"
    end
    it "#created" do
      subject.created.should_not be_nil
    end
    it "#updated" do
      subject.updated.should_not be_nil
    end
  end

  it_behaves_like "flexible initializer", :tag => "assetTag01"

  context "#from_json" do
    it "works with a full asset" do
      asset = Collins::Asset.from_json CollinsFixture.full_asset(true)
      asset.tag.should == "sl-129278"
      asset.addresses.length.should == 2
      asset.power.length.should == 2
    end
    it "works with a bare asset" do
      asset = Collins::Asset.from_json CollinsFixture.bare_asset(true), true
      asset.tag.should == "sl-102313"
      asset.addresses.length.should == 0
      asset.power.length.should == 0
    end
  end

  context "#method_missing" do
    subject { Collins::Asset.from_json(CollinsFixture.full_asset(true))}

    it "#hostname" do
      subject.hostname.should == "adminweb-83503b68.d2.tumblr.net"
    end
    it "#fizzbuzz" do
      subject.fizzbuzz.should be_nil
    end
  end

  context "full assets should have" do
    subject { Collins::Asset.from_json(CollinsFixture.full_asset(true)) }

    it "#addresses" do
      subject.addresses.length.should == 2
      subject.addresses.map {|a| a.address}.should include("4.4.4.4","10.80.97.233")
    end
    it "#ipmi" do
      subject.ipmi.should_not be_nil
      subject.ipmi.address.should == "10.80.97.234"
    end
    it "#backend_ip_address" do
      subject.backend_ip_address.should_not be_nil
      subject.backend_ip_address.should == "10.80.97.233"
    end
    it "#backend_address?" do
      subject.backend_address?.should be_true
    end
    it "#backend_ip_addresses" do
      subject.backend_ip_addresses.length.should == 1
    end
    it "#backend_netmask" do
      subject.backend_netmask.should == "255.255.248.0"
    end
    it "#mac_addresses" do
      subject.mac_addresses.length.should == 4
    end
    it "#public_ip_address" do
      subject.public_ip_address.should == "4.4.4.4"
    end
    it "#cpu_count" do
      subject.cpu_count.should == 11
    end
    it "#disks" do
      subject.disks.length.should == 2
    end
    it "#physical_nic_count" do
      subject.physical_nic_count.should == 4
    end
    it "#memory" do
      subject.memory.length.should == 24
    end
    it "#power" do
      subject.power.length.should == 2
    end
  end

end
