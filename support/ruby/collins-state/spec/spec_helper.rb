require 'simplecov'
SimpleCov.start do
  add_filter "/spec/"
end

$:.unshift(File.join(File.dirname(__FILE__), '..', 'lib'))
$:.unshift(File.dirname(__FILE__))
require 'rspec'
require 'webmock/rspec'
require 'collins_state'
require 'collins_client'

RSpec.configure do |config|
  config.mock_with :rspec
end

Encoding.default_external = Encoding::UTF_8
Dir[File.join(File.dirname(__FILE__), 'support', '**', '*.rb')].each { |f| require f }
Dir[File.join(File.dirname(__FILE__), 'shared', '**', '*.rb')].each { |f| require f }
