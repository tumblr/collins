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

  context "Update" do
    it "lshw is not an attribute" do
      ["lshw","LSHW"].each do |name|
        ::Collins::Asset::Update.is_attribute?(name).should be_false
        ::Collins::Asset::Update.get_param(name).should === "lshw"
        ::Collins::Asset::Update.is_file_param?(name).should be_true
      end
    end
    it "chassis_tag is not an attribute" do
      ["CHASSIS_TAG","chassis_tag"].each do |name|
        ::Collins::Asset::Update.is_attribute?(name).should be_false
        ::Collins::Asset::Update.get_param(name).should === "CHASSIS_TAG"
        ::Collins::Asset::Update.is_file_param?(name).should be_false
      end
    end
    it "file params support reading from files" do
      ::Collins::Asset::Update.get_param_value('lshw', 'fizz buzz').should === 'fizz buzz'
      filename = File.join(File.dirname(__FILE__), '..', 'fixtures', 'bare_asset.json')
      expected_md5 = md5(File.read(filename))
      file_contents = ::Collins::Asset::Update.get_param_value('lshw', "@#{filename}")
      actual_md5 = md5(file_contents)
      actual_md5.should === expected_md5
      expect {
        ::Collins::Asset::Update.get_param_value('lshw', '@non-existant-file')
      }.to raise_error(::Collins::ExpectationFailedError)
    end
    it "foo is an attribute" do
      ::Collins::Asset::Update.is_attribute?("foo").should be_true
      ::Collins::Asset::Update.get_param("foo").should === "foo"
      ::Collins::Asset::Update.is_attribute?("FOO").should be_true
      ::Collins::Asset::Update.get_param("FOO").should === "FOO"
    end
  end

  def md5 str
    require 'digest/md5'
    Digest::MD5.hexdigest(str)
  end
end
