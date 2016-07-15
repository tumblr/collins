# coding: utf-8
lib = File.expand_path('../lib', __FILE__)
$LOAD_PATH.unshift(lib) unless $LOAD_PATH.include?(lib)
require 'consolr/version'

Gem::Specification.new do |spec|
  spec.name          = "consolr"
  spec.version       = Consolr::VERSION
  spec.authors       = ["Will Richard", "Sashidhar Guntury", "Felix Aronsson"]
  spec.email         = ["collins-sm@googlegroups.com"]
  spec.summary       = %q{consolr is a pure ruby wrapper over IPMI to allow Out of Band communiation with nodes.}
  spec.description   = %q{Consolr is a utility which speaks to Collins on our behalf and retrieves the address, username and password to connect to that node over IPMI. Passing different flags, we can performs a variety of taks on the node over IPMI. There are safeguards in place to prevent potentially catastrophic actions being performed on nodes.}
  spec.homepage      = "https://github.com/tumblr/collins/tree/master/support/ruby/consolr"
  spec.license       = "Apache 2.0"

  spec.files         = `git ls-files -z`.split("\x0")
  spec.executables   = spec.files.grep(%r{^bin/}) { |f| File.basename(f) }
  spec.test_files    = spec.files.grep(%r{^(test|spec|features)/})
  spec.require_paths = ["lib"]

  spec.add_runtime_dependency "collins_auth", "~> 0.1"
  spec.add_runtime_dependency "net-ping", "~> 1.7"

  spec.add_development_dependency "rake", "~> 10.4"
  spec.add_development_dependency "rspec", "~> 3.3"
  spec.add_development_dependency "simplecov", "~> 0.10"
  spec.add_development_dependency "webmock", "~> 1.21"
end
