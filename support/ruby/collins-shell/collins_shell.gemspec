Gem::Specification.new do |s|
  s.name = 'collins_shell'
  s.version = File.read 'VERSION'

  s.required_rubygems_version = Gem::Requirement.new('>= 0') if s.respond_to? :required_rubygems_version=
  s.authors = ['Blake Matheny', 'Gabe Conradi', 'Will Richard']
  s.date = '2016-02-03'
  s.description = 'Provides basic CLI for interacting with Collins API'
  s.email = ['collins-sm@googlegroups.com']
  s.executables = ['collins-shell']
  s.extra_rdoc_files = [
    'README.md'
  ]
  s.files = [
    '.pryrc',
    'Gemfile',
    'Gemfile.lock',
    'README.md',
    'Rakefile',
    'VERSION',
    'bin/collins-shell',
    'collins_shell.gemspec',
    'lib/collins_shell.rb',
    'lib/collins_shell/asset.rb',
    'lib/collins_shell/asset_type.rb',
    'lib/collins_shell/cli.rb',
    'lib/collins_shell/console.rb',
    'lib/collins_shell/console/asset.rb',
    'lib/collins_shell/console/cache.rb',
    'lib/collins_shell/console/command_helpers.rb',
    'lib/collins_shell/console/commands.rb',
    'lib/collins_shell/console/commands/cat.rb',
    'lib/collins_shell/console/commands/cd.rb',
    'lib/collins_shell/console/commands/io.rb',
    'lib/collins_shell/console/commands/iterators.rb',
    'lib/collins_shell/console/commands/tail.rb',
    'lib/collins_shell/console/commands/versions.rb',
    'lib/collins_shell/console/filesystem.rb',
    'lib/collins_shell/console/options_helpers.rb',
    'lib/collins_shell/errors.rb',
    'lib/collins_shell/ip_address.rb',
    'lib/collins_shell/ipmi.rb',
    'lib/collins_shell/monkeypatch.rb',
    'lib/collins_shell/provision.rb',
    'lib/collins_shell/state.rb',
    'lib/collins_shell/tag.rb',
    'lib/collins_shell/thor.rb',
    'lib/collins_shell/util.rb',
    'lib/collins_shell/util/asset_printer.rb',
    'lib/collins_shell/util/asset_stache.rb',
    'lib/collins_shell/util/log_printer.rb',
    'lib/collins_shell/util/printer_util.rb'
  ]
  s.homepage = 'https://github.com/tumblr/collins/tree/master/support/ruby/collins-shell'
  s.licenses = ['APL 2.0']
  s.require_paths = ['lib']
  s.summary = 'Shell for Collins API'

  s.add_runtime_dependency('collins_client', '~> 0.2.20')
  s.add_runtime_dependency('highline','~> 1.6.15')
  s.add_runtime_dependency('mustache','~> 0.99.4')
  s.add_runtime_dependency('pry','~> 0.9.9.6')
  s.add_runtime_dependency('terminal-table','~> 1.4.5')
  s.add_runtime_dependency('thor','~> 0.16.0')

  s.add_development_dependency('rspec')
  s.add_development_dependency('redcarpet')
  s.add_development_dependency('yard','~> 0.8')
  s.add_development_dependency('capistrano','~> 2.15.5')
  s.add_development_dependency('rake')
  s.add_development_dependency('jeweler')
end
