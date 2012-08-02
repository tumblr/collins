require 'spec_helper'

class ApiTester
  include Collins::Api
  attr_reader :logger
  def initialize logger
    @logger = logger
  end
end

describe Collins::Api do
  it "#trace" do
    logger = double("logger")
    logger.should_receive(:debug).exactly(1).times
    a = ApiTester.new logger
    a.trace("Hello world")
  end
end
