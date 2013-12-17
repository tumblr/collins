require 'pathname'
current_dir = File.expand_path(File.dirname(__FILE__))
$:.unshift File.join(current_dir, 'scripts', 'deploy')

set :source_url, 'http://repo.tumblr.net:8888/collins.zip'

ssh_options[:keys] = Pathname.new(File.expand_path('~/.ssh')).children.select do |entry|
  entry.basename.to_s.start_with?('tumblr_')
end.map(&:to_path)

task :ewr01 do
  role :app, 'collins.ewr01.tumblr.net'
end

task :d2 do
  role :app, 'collins.d2.tumblr.net', user: 'root'
end

load 'deploy'
load 'scripts/deploy/deploy'
