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
  gem.name = 'collins_client'
  gem.homepage = 'https://github.com/tumblr/collins/tree/master/support/ruby/collins-client'
  gem.license = 'APL 2.0'
  gem.summary = %Q{Client library for Collins API}
  gem.description = "Provides ruby support for interacting with the Collins API"
  gem.email = 'bmatheny@tumblr.com'
  gem.authors = ['Blake Matheny']
  gem.files.exclude "spec/**/*"
  gem.files.exclude '.gitignore'
  gem.files.exclude '.rspec'
  gem.files.exclude '.rvmrc'
  gem.add_runtime_dependency 'httparty', '~> 0.8.3'
end

task :help do
  puts("rake -T                  # See available rake tasks")
  puts("rake publish             # generate gemspec, build it, push it to repo")
  puts("rake version:bump:patch  # Bump patch number")
  puts("rake all                 # bump patch and publish")
  puts("rake                     # Run tests")
end

task :publish => [:gemspec, :build] do
  package_abs = jeweler.jeweler.gemspec_helper.gem_path
  package_name = File.basename(package_abs)

  ["repo.tumblr.net","repo.ewr01.tumblr.net"].each do |host|
    puts("Copying #{package_abs} to #{host} and installing, you may be prompted for your password")
    system "scp #{package_abs} #{host}:"
    system "ssh -t #{host} 'sudo tumblr_gem install #{package_name}'"
  end
end

task :all => ["version:bump:patch", :publish] do
  puts("Done!")
end

require 'rspec/core'
require 'rspec/core/rake_task'
RSpec::Core::RakeTask.new(:spec) do |spec|
  spec.fail_on_error = false
  spec.pattern = FileList['spec/**/*_spec.rb']
end

task :default => :spec

YARD::Rake::YardocTask.new do |t|
  t.files = ['lib/**/*.rb']
  t.options = ['--markup', 'markdown']
end
