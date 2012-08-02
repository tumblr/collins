require 'spec_helper'

describe Collins::SimpleCallback do
  def get_cb *args
    has_block = !args.find_index{|a| a.is_a?(Proc)}.nil?
    if not has_block then
      args.unshift(Proc.new{})
    end
    Collins::SimpleCallback.new(*args)
  end

  it "#name" do
    get_cb(:fizz).name.should == :fizz
  end
  it "#options" do
    get_cb(:fizz, :opt1 => "val", :block => Proc.new{}).options.should == {:opt1 => "val"}
  end
  it "#empty" do
    Collins::SimpleCallback.empty.empty?.should be_true
    Collins::SimpleCallback.empty.defined?.should be_false
  end
  it "#defined?" do
    get_cb(:foo).defined?.should be_true
  end

  context "#arity" do
    it "0" do
      get_cb(:fizz, Proc.new{}).arity.should == 0
    end
    it "1" do
      get_cb(:fizz, Proc.new{|a| puts(a)}).arity.should == 1
    end
    it "2" do
      get_cb(:fizz, Proc.new{|a,b| puts(a,b)}).arity.should == 2
    end
  end

  context "#parameters" do
    it "abba, zabba" do
      params = get_cb(:fizz, Proc.new{|abba,zabba| puts(abba)}).parameters
      params.map{|p| p[1]}.should == [:abba, :zabba]
    end
    it "none" do
      get_cb(:fizz).parameters.should == []
    end
  end

end
