# -*- encoding: utf-8 -*-

Gem::Specification.new do |s|
  s.name = "collins_notify"
  s.version = File.read 'VERSION'

  s.required_rubygems_version = Gem::Requirement.new(">= 0") if s.respond_to? :required_rubygems_version=
  s.authors = ["Blake Matheny", "Will Richard"]
  s.date = "2015-01-06"
  s.description = "Send notifications via hipchat, slack, IRC, and email"
  s.email = "collins-sm@googlegroups.com"
  s.executables = ["collins-notify"]
  s.files = [
    "Gemfile",
    "README.rdoc",
    "Rakefile",
    "VERSION",
    "bin/collins-notify",
    "collins_notify.gemspec",
    "lib/collins_notify.rb",
    "lib/collins_notify/adapter/email.rb",
    "lib/collins_notify/adapter/helper/carried-pigeon.rb",
    "lib/collins_notify/adapter/hipchat.rb",
    "lib/collins_notify/adapter/irc.rb",
    "lib/collins_notify/adapter/slack.rb",
    "lib/collins_notify/application.rb",
    "lib/collins_notify/command_runner.rb",
    "lib/collins_notify/configuration.rb",
    "lib/collins_notify/configuration_mixin.rb",
    "lib/collins_notify/errors.rb",
    "lib/collins_notify/notifier.rb",
    "lib/collins_notify/options.rb",
    "lib/collins_notify/version.rb",
    "sample_config.yaml",
    "templates/default_email.erb",
    "templates/default_email.html.erb",
    "templates/default_hipchat.erb",
    "templates/default_irc.erb",
    "templates/default_slack.erb"
  ]
  s.homepage = "https://github.com/tumblr/collins/tree/master/support/ruby/collins-notify"
  s.licenses = ["MIT"]
  s.require_paths = ["lib"]
  s.rubygems_version = "1.8.23"
  s.summary = "Notifications for Collins"

  s.add_runtime_dependency('addressable', '~> 2.3.2')
  s.add_runtime_dependency('collins_client', '~> 0.2.10')
  s.add_runtime_dependency('hipchat', '~> 0.7.0')
  s.add_runtime_dependency('mail', '~> 2.4.4')
  s.add_runtime_dependency('nokogiri', '~> 1.5.2')

  s.add_development_dependency('i18n','< 0.7.0')
  s.add_development_dependency('rspec','~> 2.12.0')
  s.add_development_dependency('yard','~> 0.8.3')
  s.add_development_dependency('rdoc','~> 3.12')
  s.add_development_dependency('bundler','>= 1.2.0')
  s.add_development_dependency('simplecov','~> 0.9.1')
  s.add_development_dependency('rake')

end

