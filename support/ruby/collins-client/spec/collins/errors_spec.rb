require 'spec_helper'

describe Collins::RichRequestError do

  context "a rich context error should have" do
    subject {
      err = Collins::RichRequestError.new(
        "ruby message", 400, "server message",
        Hash[
          "classOf" => "java.lang.Exception",
          "message" => "internal server message",
          "stackTrace" => "a stacktrace"
        ]
      )
      err.uri = "/api/assets"
      err
    }

    it "#class_of" do
      subject.class_of.should == "java.lang.Exception"
    end
    it "#message" do
      subject.message.should == "ruby message"
    end
    it "#code" do
      subject.code.should == 400
    end
    it "#remote_description" do
      subject.remote_description.should == "server message"
    end
    it "#remote_message" do
      subject.remote_message.should == "internal server message"
    end
    it "#stacktrace" do
      subject.stacktrace.should == "a stacktrace"
    end
    it "#uri" do
      subject.uri.should == "/api/assets"
    end
  end

end
