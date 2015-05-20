# -*- encoding: utf-8 -*-

Gem::Specification.new do |s|
  s.name = "collins_state"
  s.version = File.read 'VERSION'

  s.authors = ["Blake Matheny"]
  s.date = "2015-05-20"
  s.description = "Provides basic framework for managing stateful processes with collins"
  s.email = "bmatheny@tumblr.com"
  s.extra_rdoc_files = [
    "README.md"
  ]
  s.files = [
    "Gemfile",
    "Gemfile.lock",
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

  s.add_runtime_dependency 'collins_client'
  s.add_runtime_dependency 'escape'

  s.add_development_dependency 'rspec', '~> 2.99'
  s.add_development_dependency 'yard'
  s.add_development_dependency 'redcarpet'
  s.add_development_dependency 'webmock'
  s.add_development_dependency 'bundler'
  s.add_development_dependency 'simplecov'
  s.add_development_dependency 'rake'
end

