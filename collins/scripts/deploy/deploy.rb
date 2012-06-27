# app options
set :application, 'collins'
set :repository,  '.'

# deploy options
set :scm,        :none
set :deploy_via, :wget
set :copy_strategy, :wget
#set :wget_local, true
set :source_url, 'http://10.60.26.92:8888/collins.zip'
set :deploy_to,  '/usr/local/collins'

# user options
set :user,       'deploy'
set :use_sudo,   true

# ssh options
ssh_options[:keys] = ['~/.ssh/tumblr_deploy']
ssh_options[:forward_agent] = true

# misc options
default_run_options[:pty]   = true
set :normalize_asset_timestamps, false

# nodes
role :app, 'collins-24d1214f.ewr01.tumblr.net'
role :appd2, 'collins-8a02f07e.d2.tumblr.net'
role :dev, 'localhost', :user => ENV['USER']

# tasks
namespace :deploy do
  task :default do
    update
    restart
  end

  task :start do
    run "#{sudo :as => 'collins'} /usr/local/collins/current/scripts/collins.sh start"
  end

  task :fix_permissions do
    run "chmod 777 #{fetch(:release_path)}"
  end

  task :stop do
    run "#{sudo :as => 'collins'} /usr/local/collins/current/scripts/collins.sh stop"
  end

  task :copy_config do
    run "#{sudo :as => 'collins'} cp #{fetch(:previous_release)}/conf/production.conf #{fetch(:current_path)}/conf/production.conf" if fetch(:previous_release)
  end

  task :status do
    run "#{sudo :as => 'collins'} /usr/local/collins/current/scripts/collins.sh status"
  end

  task :restart do
    stop
    start
  end

end

after 'deploy:update', 'deploy:copy_config'
after 'deploy:update', 'deploy:fix_permissions'
after 'deploy:update', 'deploy:cleanup'
