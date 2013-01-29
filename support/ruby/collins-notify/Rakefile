# encoding: utf-8

require 'rubygems'
require 'bundler'
begin
  Bundler.setup(:default, :development)
rescue Bundler::BundlerError => e
  $stderr.puts e.message
  $stderr.puts "Run `bundle install` to install missing gems"
  exit e.status_code
end
require 'rake'
require 'jeweler'
require 'yard'

jeweler = Jeweler::Tasks.new do |gem|
  # gem is a Gem::Specification... see http://docs.rubygems.org/read/chapter/20 for more options
  gem.name = "collins_notify"
  gem.homepage = "https://github.com/tumblr/collins/tree/master/support/ruby/collins-notify"
  gem.license = "MIT"
  gem.summary = %Q{Notifications for Collins}
  gem.description = %Q{Send notifications via hipchat, IRC and email}
  gem.email = "bmatheny@tumblr.com"
  gem.authors = ["Blake Matheny"]
  %w[config.yaml spec/**/* .gitignore .rspec .rvmrc .document .rbenv-version].each do |fp|
    gem.files.exclude fp
  end
  gem.add_runtime_dependency 'collins_client', '~> 0.2.10'
  gem.add_runtime_dependency 'hipchat',        '~> 0.4.1'
  gem.add_runtime_dependency 'mail',           '~> 2.4.4'
  gem.add_runtime_dependency 'nokogiri',       '~> 1.5.2'
end

task :help do
  puts("rake -T                  # See available rake tasks")
  puts("rake spec                # Run tests")
  puts("rake make                # version:bump:patch gemspec build")
  puts("rake publish             # run copy_gem")
  puts("rake all                 # make then publish")
  puts("rake                     # this help")
end

task :publish do
  package_abs = jeweler.jeweler.gemspec_helper.gem_path
  package_name = File.basename(package_abs)

  puts "Run 'copy_gem #{package_name}'"
end

task :make => ["version:bump:patch", "gemspec", "build"] do
  puts("Done!")
end

task :all => [:make, :publish]

require 'rspec/core'
require 'rspec/core/rake_task'
RSpec::Core::RakeTask.new(:spec) do |spec|
  spec.fail_on_error = false
  spec.pattern = FileList['spec/**/*_spec.rb']
end

RSpec::Core::RakeTask.new(:rcov) do |spec|
  spec.pattern = 'spec/**/*_spec.rb'
  spec.rcov = true
end

task :default => :help
task :rspec => :spec

YARD::Rake::YardocTask.new do |t|
  t.files = ['lib/**/*.rb']
  t.options = ['--markup', 'markdown']
end
