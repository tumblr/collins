Gem::Specification.new do |s|
  s.name = "collins_client"
  s.version = File.read 'VERSION'

  s.required_rubygems_version = Gem::Requirement.new(">= 0") if s.respond_to? :required_rubygems_version=
  s.authors = ["Blake Matheny", "Gabe Conradi", "Will Richard"]
  s.date = "2015-03-17"
  s.description = "Provides ruby support for interacting with the Collins API"
  s.email = ["collins-sm@googlegroups.com","gabe@tumblr.com","will@tumblr.com"]
  s.extra_rdoc_files = [
    "README.md"
  ]
  s.files = [
    "Gemfile",
    "Gemfile.lock",
    "README.md",
    "Rakefile",
    "VERSION",
    "collins_client.gemspec",
    "lib/collins/address.rb",
    "lib/collins/api.rb",
    "lib/collins/api/admin.rb",
    "lib/collins/api/asset.rb",
    "lib/collins/api/asset_state.rb",
    "lib/collins/api/asset_type.rb",
    "lib/collins/api/attributes.rb",
    "lib/collins/api/ip_address.rb",
    "lib/collins/api/logging.rb",
    "lib/collins/api/management.rb",
    "lib/collins/api/tag.rb",
    "lib/collins/api/util.rb",
    "lib/collins/api/util/errors.rb",
    "lib/collins/api/util/parameters.rb",
    "lib/collins/api/util/requests.rb",
    "lib/collins/api/util/responses.rb",
    "lib/collins/asset.rb",
    "lib/collins/asset_client.rb",
    "lib/collins/asset_find.rb",
    "lib/collins/asset_type.rb",
    "lib/collins/asset_update.rb",
    "lib/collins/client.rb",
    "lib/collins/errors.rb",
    "lib/collins/ipmi.rb",
    "lib/collins/logging.rb",
    "lib/collins/monkeypatch.rb",
    "lib/collins/option.rb",
    "lib/collins/power.rb",
    "lib/collins/profile.rb",
    "lib/collins/simple_callback.rb",
    "lib/collins/state.rb",
    "lib/collins/util.rb",
    "lib/collins_client.rb"
  ]
  s.homepage = "https://github.com/tumblr/collins/tree/master/support/ruby/collins-client"
  s.licenses = ["APL 2.0"]
  s.require_paths = ["lib"]
  s.summary = "Client library for Collins API"

  s.required_ruby_version = '>= 1.9.2'

  s.add_runtime_dependency 'httparty', '~> 0.11.0'

  s.add_development_dependency 'yard'
  s.add_development_dependency 'redcarpet'
  s.add_development_dependency 'rspec', '~> 2.99'
  s.add_development_dependency 'webmock'
  s.add_development_dependency 'simplecov'
  s.add_development_dependency 'rake'
end
