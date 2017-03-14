# -*- encoding: utf-8 -*-

Gem::Specification.new do |s|
  s.name = "collins_state"
  s.version = File.read 'VERSION'

  s.authors = ["Blake Matheny"]
  s.date = "2015-05-20"
  s.description = "Provides basic framework for managing stateful processes with collins"
  s.email = "collins-sm@googlegroups.com"
  s.extra_rdoc_files = [
    "README.md"
  ]
  s.files = [
    "Gemfile",
    "README.md",
    "Rakefile",
    "VERSION",
    "lib/collins/persistent_state.rb",
    "lib/collins/state/mixin.rb",
    "lib/collins/state/mixin_class_methods.rb",
    "lib/collins/state/specification.rb",
    "lib/collins/workflows/provisioning_workflow.rb",
    "lib/collins_state.rb"
  ]
  s.homepage = "https://github.com/tumblr/collins/tree/master/support/ruby/collins-state"
  s.licenses = ["APL 2.0"]
  s.require_paths = ["lib"]
  s.summary = "Collins based state management"

  s.required_ruby_version = '>= 1.9.2'

  s.add_runtime_dependency 'collins_client', '~> 0.2.7'
  s.add_runtime_dependency 'escape', '~> 0.0.4'

  s.add_development_dependency 'rspec', '~> 2.99'
  s.add_development_dependency 'yard', '~> 0.8.7'
  s.add_development_dependency 'redcarpet', '~> 3.2'
  s.add_development_dependency 'webmock', '~> 1.21'
  s.add_development_dependency 'bundler'
  s.add_development_dependency 'simplecov'
  s.add_development_dependency 'rake', '~> 10.5'
end

