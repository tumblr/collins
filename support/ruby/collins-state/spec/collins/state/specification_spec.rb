require 'spec_helper'

describe Collins::State::Specification do

  def create name = nil, description = nil, timestamp = nil
    Collins::State::Specification.new name, description, timestamp
  end

  context "#empty" do
    it "empty?" do
      Collins::State::Specification.empty.empty?.should be_true
    end
    it "defined?" do
      Collins::State::Specification.empty.defined?.should be_false
    end
  end

  context "#initialize" do
    it "no name throws exception" do
      expect { create }.to raise_error(ArgumentError)
    end
    it "no description throws exception" do
      expect { create "name" }.to raise_error(ArgumentError)
    end
    it "create from args" do
      s = create "my name", "my desc"
      s.name.should == "my name".to_sym
      s.description.should == "my desc"
    end
    it "has a default timestamp" do
      s = create "my name", "my desc"
      s.timestamp.should == 0
    end
    it "handles string timestamps" do
      t = Time.now.utc.to_i
      s = create "my name", "my desc", t.to_s
      s.timestamp.should == t
    end
    it "handles fixnum timestamps" do
      t = Time.now.utc.to_i
      s = create "my name", "my desc", t
      s.timestamp.should == t
    end
    it "handles Time timestamps" do
      t = Time.now.utc
      s = create "my name", "my desc", t
      s.timestamp.should == t.to_i
    end
    it "bail on non-Time/Fixnum/String args" do
      expect { create("my name", "my desc", (0..3)) }.to raise_error(ArgumentError)
    end
    it "discards args appropriately" do
      s = Collins::State::Specification.new :name => "my name", :description => "my desc"
      s.extras.should be_empty
    end

    it "handles extra args" do
      s = Collins::State::Specification.new "my name", "my desc", 0, :stuff => :thing, :foo => :bar
      s.extras.should_not be_empty
      s.extras.keys.size.should == 2
    end
    it "handles flattening extra args" do
      s = Collins::State::Specification.new "my name", "my desc", 0, :extras => {:stuff => :thing, :foo => :bar}
      s.extras.keys.size.should == 2
    end
  end # context initialize

  context "acts like hash" do
    subject { Collins::State::Specification.new("my name", "my desc", 0, :thing => :thung, :fizz => :buzz) }
    it "#[]" do
      subject[:thing].should == :thung
      subject[:lkajsd].should be_nil
    end
    it "#key?" do
      subject.key?(:thing).should be_true
      subject.key?(:lkajsd).should be_false
    end
    it "#fetch" do
      subject.fetch(:thing, :stuff).should == :thung
      subject.fetch(:lkajs, :stuff).should == :stuff
    end
    it "#[]=" do
      subject[:thing].should == :thung
      subject[:thing] = :thang
      subject[:thing].should == :thang
      subject[:lkas].should be_nil
      subject[:lkas] = :foo
      subject[:lkas].should == :foo
    end
    it "#to_hash" do
      hash = subject.to_hash
      hash[:name].should == "my name".to_sym
      hash[:description].should == "my desc"
      hash[:timestamp].should == 0
      hash[:extras][:thing].should == :thung
      hash[:extras][:fizz].should == :buzz
    end
  end

  context "comparison" do
    def instance
      Collins::State::Specification.new("my name", "my desc", 0, :thing => :thung, :fizz => :buzz)
    end
    it "sees different classes not being equal" do
      instance.should_not == "stuff"
    end
    it "sees same class, same name/desc/ts as equal" do
      i1 = instance
      i2 = instance
      i1.should == i2
    end
    it "sees same class, different names as not equal" do
      i1 = instance
      i2_hash = instance.to_hash
      i2_hash[:name] = "other name"
      i2 = Collins::State::Specification.new(i2_hash)
      i1.should_not == i2
    end
    it "sees same class, different timestamps as not equal" do
      i1 = instance
      i2_hash = instance.to_hash
      i2_hash[:timestamp] = 42
      i2 = Collins::State::Specification.new(i2_hash)
      i1.should_not == i2
    end
    it "sees same class, different descriptions as equal" do
      i1 = instance
      i2_hash = instance.to_hash
      i2_hash[:description] = "stuff here"
      i2 = Collins::State::Specification.new(i2_hash)
      i1.should == i2
    end
    it "sees same class, different extras as equal" do
      i1 = instance
      i2_hash = instance.to_hash
      i2_hash[:extras][:person] = "henry"
      i2 = Collins::State::Specification.new(i2_hash)
      i1.should == i2
    end
  end

  context "serialization" do
    def instance
      Collins::State::Specification.new("my name", "my desc", 0, :thing => :thung, :fizz => :buzz)
    end

    it "#to_json" do
      i = instance
      json = JSON.dump(i)
      JSON.parse(json, :create_additions => true).should == i
    end

    it "#to_option" do
      instance.to_option.defined?.should be_true
      Collins::State::Specification.empty.to_option.defined?.should be_false
    end

    it "#to_s" do
      i = instance
      i.to_s.should include("Specification(name = my name")
      i.to_s.should include("timestamp = 0")
    end

  end

end
