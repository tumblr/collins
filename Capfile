current_dir = File.expand_path(File.dirname(__FILE__))

$:.unshift File.join(current_dir, 'scripts', 'deploy')

set :source_url, 'http://repo.tumblr.net:8888/collins.zip'
ssh_options[:keys] = ['~/.ssh/tumblr_deploy','~/.ssh/tumblr_root_dsa','~/.ssh/tumblr_root_rsa']
role :app, 'collins-24d1214f.ewr01.tumblr.net'
role :appewr01, 'collins-3d74f391.ewr01.tumblr.net'
role :appd2, 'collins-8a02f07e.d2.tumblr.net', :user => 'root'

load 'deploy'
load 'scripts/deploy/deploy'
