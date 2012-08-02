require 'spec_helper'

class CollinsUtilClass
end

describe Collins::Util do
  subject {
    c = CollinsUtilClass.new
    c.extend(Collins::Util)
  }

  context "#deep_copy_hash" do
    it "base case" do
      hash = {:foo => "bar", :fizz => "buzz"}
      new_hash = subject.deep_copy_hash(hash)
      new_hash[:foo] = "test"
      hash[:foo].should_not == new_hash[:foo]
    end
    it "without hash" do
      expect { subject.deep_copy_hash("not a hash") }.to raise_error(Collins::ExpectationFailedError)
    end
  end

  context "#require_non_empty" do
    it "empty string" do
      expect {subject.require_non_empty("  ", "test")}.to raise_error(Collins::ExpectationFailedError)
    end
    it "empty array" do
      expect {subject.require_non_empty([], "test")}.to raise_error(Collins::ExpectationFailedError)
    end
    it "nil" do
      expect {subject.require_non_empty(nil, "test")}.to raise_error(Collins::ExpectationFailedError)
    end
    it "returns value if not null" do
      subject.require_non_empty("stuff", "test", true).should == "stuff"
    end
    it "returns other value if specified" do
      subject.require_non_empty("stuff", "test", "fizz").should == "fizz"
    end
    it "returns nil if return_value == false" do
      subject.require_non_empty("stuff", "test", false).should be_nil
    end
  end

  it "#require_that" do
    subject.require_that("foo", "message", true).should == "foo"
  end

  context "#symbolize_hash" do
    it "empty on empty" do
      subject.symbolize_hash({}).should == {}
    end
    it "exception on non-hash" do
      expect {subject.symbolize_hash("not hash")}.to raise_error(Collins::ExpectationFailedError)
    end
    it "base case" do
      actual = {"foo" => "bar", :fizz => "buzz", :quz => :qaz}
      expected = {:foo => "bar", :fizz => "buzz", :quz => :qaz}
      subject.symbolize_hash(actual).should == expected
    end
    it "downcases if requested" do
      actual = {"FOO" => "bar", :fizz => "buzz", :QUZ => :qaz}
      expected = {:foo => "bar", :fizz => "buzz", :quz => :qaz}
      subject.symbolize_hash(actual, :downcase => true).should == expected
    end
    it "does not downcase if not requested" do
      actual = {"FOO" => "bar", :fizz => "buzz", :QUZ => :qaz}
      expected = {:FOO => "bar", :fizz => "buzz", :QUZ => :qaz}
      subject.symbolize_hash(actual, :downcase => false).should == expected
    end
    it "rewrites regex if requested" do
      re = /.*foo.*/
      actual = {"foo" => "bar", :fizz => re, :quz => :qaz}
      expected = {:foo => "bar", :fizz => ".*foo.*", :quz => :qaz}
      subject.symbolize_hash(actual, :rewrite_regex => true).should == expected
    end
    it "does not rewrite regex if not requested" do
      re = /.*foo.*/
      actual = {"foo" => "bar", :fizz => re, :quz => :qaz}
      expected = {:foo => "bar", :fizz => re, :quz => :qaz}
      subject.symbolize_hash(actual, :rewrite_regex => false).should == expected
    end
    it "hash of hashes" do
      actual = {"foo" => "bar", :fizz => "buzz", :quz => {:qux => "lkas", "abc" => "def"}}
      expected = {:foo => "bar", :fizz => "buzz", :quz => {:qux => "lkas", :abc => "def"}}
      subject.symbolize_hash(actual).should == expected
    end
  end

  context "#stringify_hash" do
    it "empty on empty" do
      subject.stringify_hash({}).should == {}
    end
    it "exception on non-hash" do
      expect {subject.stringify_hash("not hash")}.to raise_error(Collins::ExpectationFailedError)
    end
    it "base case" do
      actual = {"foo" => "bar", :fizz => "buzz", :quz => :qaz}
      expected = {"foo" => "bar", "fizz" => "buzz", "quz" => :qaz}
      subject.stringify_hash(actual).should == expected
    end
    it "downcases if requested" do
      actual = {"FOO" => "bar", :FIZZ => "buzz", "quz" => :qaz}
      expected = {"foo" => "bar", "fizz" => "buzz", "quz" => :qaz}
      subject.stringify_hash(actual, :downcase => true).should == expected
    end
    it "does not downcase if not requested" do
      actual = {"FOO" => "bar", :fizz => "buzz", :QUZ => :qaz}
      expected = {"FOO" => "bar", "fizz" => "buzz", "QUZ" => :qaz}
      subject.stringify_hash(actual, :downcase => false).should == expected
    end
    it "rewrites regex if requested" do
      re = /.*foo.*/
      actual = {"foo" => "bar", :fizz => re, :quz => :qaz}
      expected = {"foo" => "bar", "fizz" => ".*foo.*", "quz" => :qaz}
      subject.stringify_hash(actual, :rewrite_regex => true).should == expected
    end
    it "does not rewrite regex if not requested" do
      re = /.*foo.*/
      actual = {"foo" => "bar", :fizz => re, :quz => :qaz}
      expected = {"foo" => "bar", "fizz" => re, "quz" => :qaz}
      subject.stringify_hash(actual, :rewrite_regex => false).should == expected
    end
    it "hash of hashes" do
      actual = {"foo" => "bar", :fizz => "buzz", :quz => {:qux => "lkas", "abc" => "def"}}
      expected = {"foo" => "bar", "fizz" => "buzz", "quz" => {"qux" => "lkas", "abc" => "def"}}
      subject.stringify_hash(actual).should == expected
    end
  end

end
