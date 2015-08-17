require 'rspec/core'
require 'rspec/core/rake_task'
RSpec::Core::RakeTask.new(:spec) do |spec|
  spec.fail_on_error = true
  spec.pattern = FileList['spec/**/*_spec.rb']
end

task :default => :spec
