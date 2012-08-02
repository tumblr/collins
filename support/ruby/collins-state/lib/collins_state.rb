$:.unshift File.join File.dirname(__FILE__)
require 'collins_client'
require 'collins/persistent_state'
Dir[File.join(File.dirname(__FILE__), 'collins', 'workflows', '*.rb')].each {|f| require f}
