require 'rspec/expectations'

RSpec::Matchers.define :have_power_action do |expected|
  match do |key|
    Collins::Power.normalize_action(key) == expected
  end
  description do
    "have power action"
  end
  failure_message_for_should do |actual|
    "expected power action for #{actual} to be #{expected}, was #{Collins::Power.normalize_action(actual)}"
  end
end

RSpec::Matchers.define :be_an_asset do |options|
  match do |asset|
    asset.is_a?(Collins::Asset) && !options.map {|k,v| asset.send(k) == v}.uniq.include?(false)
  end
end
