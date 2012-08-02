require 'spec_helper'

describe Collins::Option do

  def get_some value = "henry"
    Collins::Some(value)
  end
  def get_none
    Collins::None()
  end

  context "Option" do
    it "nil == None" do
      none = get_none
      Collins::Option(nil).should == none
    end
    it "some == Some(some)" do
      some = get_some("stuff")
      Collins::Option("stuff").should == some
    end
    it "works with false" do
      Collins::Option(false).should == get_some(false)
    end
  end

  it "#empty?" do
    expect { Collins::Option.new.empty? }.to raise_error(NotImplementedError)
  end
  it "#defined?" do
    expect { Collins::Option.new.defined? }.to raise_error(NotImplementedError)
  end
  it "#get" do
    expect { Collins::Option.new.get }.to raise_error(NotImplementedError)
  end

  context "#get_or_else" do
    it "not run with some" do
      some = get_some("stuff")
      some.get_or_else("foo").should == "stuff"
    end
    it "with value" do
      none = get_none
      none.get_or_else("stuff").should == "stuff"
    end
    it "with block" do
      none = get_none
      none.get_or_else {
        i = 7
        j = 3
        (i + j).to_s
      }.should == "10"
    end
  end

  context "#or_else" do
    it "not run when some" do
      some = get_some("stuff")
      some.or_else("thing").get.should == "stuff"
    end
    it "with value" do
      none = get_none
      none.or_else(get_some("stuff")).get.should == "stuff"
    end
    it "with block" do
      none = get_none
      none.or_else {
        i = 7
        j = 3
        get_some((i + j).to_s)
      }.get.should == "10"
    end
  end

  context "#exists?" do
    it "false and not executed when none" do
      code = double("tester")
      code.should_receive(:work).exactly(0).times
      none = get_none
      none.exists? { |v|
        code.work(v)
      }.should be_false
    end
    it "false and execute when some" do
      get_some("henry").exists? { |v|
        v != "henry"
      }.should be_false
    end
    it "true and execute when some" do
      get_some("henry").exists? { |v|
        v == "henry"
      }.should be_true
    end
  end

  context "#foreach" do
    it "not run when none" do
      worker = double("worker")
      worker.should_receive(:work).exactly(0).times
      get_none.foreach { |v|
        worker.work(v)
      }
    end
    it "run when some, nil return" do
      worker = double("worker")
      worker.should_receive(:work).exactly(1).times.with("stuff")
      get_some("stuff").foreach { |v|
        worker.work(v)
      }.should be_nil
    end
  end

  context "#map" do
    it "not run when none" do
      worker = double("worker")
      worker.should_receive(:work).exactly(0).times
      get_none.map { |v| worker.work(v) }
    end
    it "run when some" do
      get_some("13").map{|i| i.to_i}.get.should == 13
    end
  end

  context "#flat_map" do
    it "not run when none" do
      worker = double("worker")
      worker.should_receive(:work).exactly(0).times
      get_none.flat_map { |v| worker.work(v) }.should == get_none
    end
    it "flatten when needed" do
      get_some("stuff").flat_map {|v| get_some("stuff #{v}")}.get.should == "stuff stuff"
    end
    it "not flatten if not needed" do
      get_some("stuff").flat_map {|v| "stuff #{v}"}.get.should == "stuff stuff"
    end
  end

  context "#filter" do
    it "not run when none" do
      worker = double("worker")
      worker.should_receive(:work).exactly(0).times
      get_none.filter { |v| worker.work(v) }.should == get_none
    end
    it "convert to none when predicate returns false" do
      get_some(42).filter{|i| i > 50}.should == get_none
    end
    it "keep as some when predicate is true" do
      get_some(42).filter{|i| i > 40}.should == get_some(42)
    end
  end

  context "#filter_not" do
    it "not run when none" do
      worker = double("worker")
      worker.should_receive(:work).exactly(0).times
      get_none.filter_not { |v| worker.work(v) }.should == get_none
    end
    it "convert to none when predicate is true" do
      get_some("  ").map{|s| s.strip}.filter_not{|s| s.empty?}.should == get_none
    end
    it "keep as some when predicate is false" do
      get_some(" herro ").map{|s| s.strip}.filter_not{|s| s.empty?}.should == get_some("herro")
    end
  end

end
