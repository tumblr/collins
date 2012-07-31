# app options
set :application, 'collins'
set :repository,  '.'

# deploy options
set :scm,        :none
set :deploy_via, :wget
set :copy_strategy, :wget
#set :wget_local, true
set :deploy_to,  '/usr/local/collins'

# user options
set :user,       'root'
set :use_sudo,   true
set :admin_runner, 'collins'

# ssh options
ssh_options[:forward_agent] = true

# misc options
default_run_options[:pty]   = true
set :normalize_asset_timestamps, false

# nodes
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
