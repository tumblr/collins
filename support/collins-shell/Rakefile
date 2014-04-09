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

jeweler = Jeweler::Tasks.new do |gem|
  gem.name = 'collins_shell'
  gem.homepage = 'https://github.com/tumblr/collins/tree/master/support/collins-shell'
  gem.license = 'APL 2.0'
  gem.summary = %Q{Shell for Collins API}
  gem.description = "Provides basic CLI for interacting with Collins API"
  gem.email = 'bmatheny@tumblr.com'
  gem.authors = ['Blake Matheny']
  gem.files.exclude "spec/**/*"
  gem.files.exclude '.gitignore'
  gem.files.exclude '.rspec'
  gem.add_runtime_dependency 'collins_client',  '~> 0.2.10'
  gem.add_runtime_dependency 'highline',        '~> 1.6.15'
  gem.add_runtime_dependency 'mustache',        '~> 0.99.4'
  gem.add_runtime_dependency 'pry',             '~> 0.9.9.6'
  gem.add_runtime_dependency 'rubygems-update', '~> 1.8.24'
  gem.add_runtime_dependency 'terminal-table',  '~> 1.4.5'
  gem.add_runtime_dependency 'thor',            '~> 0.16.0'
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

task :default => :yard

require 'yard'
YARD::Rake::YardocTask.new do |t|
  t.files = ['lib/**/*.rb']
  t.options = ['--markup', 'markdown']
end
