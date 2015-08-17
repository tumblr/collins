require 'simplecov'
SimpleCov.start do
  add_filter "/spec/"
end

require 'consolr'

# require our mocks
require 'mocks/collins_auth'
require 'mocks/collins'
require 'mocks/ping'
require 'webmock/rspec'

WebMock.disable_net_connect!()

# make sure we use the rspec config file
ENV['CONSOLR_CONFIG'] = "#{File.dirname(__FILE__)}/configs/consolr_rspec.yml"
ENV['COLLINS_CLIENT_CONFIG'] = "#{File.dirname(__FILE__)}/configs/collins_rspec.yml"

RSpec.configure do |config|
  config.include MockCollinsAuthMixin
  config.include MockCollinsMixin
  config.include MockPingMixin

  config.before(:example) do |example|
    mock_collins_auth
    mock_collins
    mock_ping
  end

end
