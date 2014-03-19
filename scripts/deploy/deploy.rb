# app options
set :application, 'collins'
set :repository,  '.'

# deploy options
set :scm,        :none
set :deploy_via, :wget
set :copy_strategy, :wget
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

# tumblr options
set :release_server, 'repo.tumblr.net'
set :release_location, File.join(File.dirname(__FILE__), '../../target/collins.zip')
set :remote_release_location, '/usr/local/static_file_server'

# tasks
namespace :publish do
  task :collins do
    set :user, ENV['user']
    abort "unable to find packaged collins release @ #{release_location}" unless File.exist?(release_location)

    release_file_name = ['collins', File.mtime(release_location).to_i, 'zip'].join('.')
    upload_target_location = File.join '/tmp', release_file_name
    upload release_location, upload_target_location, hosts: [release_server]

    sudo [
      "mv #{upload_target_location} #{remote_release_location}",
      "ln -fs #{File.join(remote_release_location, release_file_name)} #{File.join(remote_release_location, 'collins.zip')}"
    ].join(' && '), hosts: [release_server]
  end
end

namespace :deploy do
  task :default do
    update
    restart
  end

  task :start do
    sudo "/usr/local/collins/current/scripts/collins.sh start", :as => "collins"
  end

  task :fix_permissions do
    run "chown -R collins /usr/local/collins"
  end

  task :stop do
    sudo "/usr/local/collins/current/scripts/collins.sh stop", :as => "collins"
  end

  task :copy_config do
    sudo "cp #{fetch(:previous_release)}/conf/production.conf #{fetch(:current_path)}/conf/production.conf", :as => "collins" if fetch(:previous_release)
  end

  task :status do
    sudo "/usr/local/collins/current/scripts/collins.sh status", :as => "collins"
  end

  task :restart do
    stop
    start
  end
end

after 'deploy:update', 'deploy:copy_config'
after 'deploy:update', 'deploy:fix_permissions'
after 'deploy:update', 'deploy:cleanup'
