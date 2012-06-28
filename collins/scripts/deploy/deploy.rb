# app options
set :application, 'collins'
set :repository,  '.'

# deploy options
set :scm,        :none
set :deploy_via, :wget
set :copy_strategy, :wget
#set :wget_local, true
set :source_url, 'http://10.80.97.195:8888/collins.zip'
set :deploy_to,  '/usr/local/collins'

# user options
set :user,       'root'
set :use_sudo,   true
set :admin_runner, 'collins'

# ssh options
ssh_options[:keys] = ['~/.ssh/tumblr_deploy','~/.ssh/tumblr_root_dsa','~/.ssh/tumblr_root_rsa']
ssh_options[:forward_agent] = true

# misc options
default_run_options[:pty]   = true
set :normalize_asset_timestamps, false

# nodes
role :app, 'collins-24d1214f.ewr01.tumblr.net'
role :appd2, 'collins-8a02f07e.d2.tumblr.net', :user => 'root'
role :dev, 'localhost', :user => ENV['USER']

# tasks
namespace :deploy do
  task :default do
    update
    restart
  end

  task :start do
    run "/usr/local/collins/current/scripts/collins.sh start"
  end

  task :fix_permissions do
    run "chown -R collins /usr/local/collins"
  end

  task :stop do
    run "/usr/local/collins/current/scripts/collins.sh stop"
  end

  task :copy_config do
    run "cp #{fetch(:previous_release)}/conf/production.conf #{fetch(:current_path)}/conf/production.conf" if fetch(:previous_release)
  end

  task :status do
    run "/usr/local/collins/current/scripts/collins.sh status"
  end

  task :restart do
    stop
    start
  end

end

after 'deploy:update', 'deploy:copy_config'
after 'deploy:update', 'deploy:fix_permissions'
after 'deploy:update', 'deploy:cleanup'
