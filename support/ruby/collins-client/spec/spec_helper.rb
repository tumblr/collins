require 'simplecov'
SimpleCov.start do
  add_filter "/spec/"
end

require 'rubygems'
require 'rspec/core'
require 'json'
require 'webmock/rspec'

if RUBY_VERSION !~ /^1\.9/ then
  raise Exception.new("We require ruby 1.9.x")
end

$:.unshift File.expand_path(File.join('..', 'lib'))
require 'collins_client'

RSpec.configure do |config|
  config.mock_with :rspec
  config.before(:suite) {
  }
end

Encoding.default_external = Encoding::UTF_8
Dir[File.join(File.dirname(__FILE__), 'support', '**', '*.rb')].each { |f| require f }
Dir[File.join(File.dirname(__FILE__), 'shared', '**', '*.rb')].each { |f| require f }
