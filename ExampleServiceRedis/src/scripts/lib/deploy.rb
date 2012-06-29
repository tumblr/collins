default_run_options[:pty] = true

if not exists?(:application) then
  raise Exception.new(":application not defined")
end
if not exists?(:repository) then
  raise Exception.new(":repository not defined")
end

set :scm, :none
set :repository, File.expand_path(File.dirname(__FILE__) + "./../../../dist/#{fetch(:application)}")
set :deploy_path, "/usr/local/#{fetch(:application)}"
set :deploy_to, deploy_path
set :releases_path, File.join(deploy_path, version_dir)
set :shared_path, File.join(deploy_path, shared_dir)
set :current_path, File.join(deploy_path, current_dir)
set :release_path, File.join(releases_path, release_name)
set :copy_compression, :zip
set :copy_cache, true
set :ssh_options, {:forward_agent => true}
set :strategy, Capistrano::Deploy::Strategy::CustomCopy.new(self)

if ENV.has_key?('SUDO_PASSWORD') then
  set :password, ENV['SUDO_PASSWORD']
end

def is_root
  system("[ `whoami` = root ]")
end

# would be lovely to memoize
def sudo_if_needed()
  if(is_root)
    ""
  else
    "#{try_sudo}"
  end
end

["log","run"].each do |dir|                                                                                                       
  ddir = "/var/#{dir}/#{fetch(:application)}"                                                                                     
  unless system("#{sudo_if_needed} test -d #{ddir}") \
     and system("#{sudo_if_needed} test -w #{ddir}")                                                                              
    run_locally("#{sudo_if_needed} mkdir -p #{ddir}")                                                                             
  end                                                                                                                             
end  

UPLOAD_HOST = "repo.tumblr.net"
set :upload_host, UPLOAD_HOST

every_deploy_task = namespaces[:deploy].tasks.keys.map{|name| "deploy:#{name.to_s.rstrip}"}
before every_deploy_task, "environment:check"
after "deploy:setup" do
  transaction do
    run_locally "if [ ! -d #{heapster_dir} ]; then if [ ! -f /usr/local/lib/libheapster.jnilib ]; then #{install_heapster}; fi fi"
    run_locally "if [ ! -x /usr/local/bin/daemon ]; then #{install_daemon}; fi"
  end
end

def install_daemon
  daemon_version = "daemon-0.6.4"
  daemon_dir = "/tmp/#{daemon_version}"
  on_rollback do
    run_locally("#{sudo_if_needed} rm -rf #{daemon_dir}")
    run_locally("#{sudo_if_needed} rm -f #{daemon_dir}.tar.gz")
  end
  wget = wget_cmd(:file => "#{daemon_version}.tar.gz", :local_file => "/tmp/#{daemon_version}.tar.gz")
  mkdir = "mkdir #{daemon_dir}"
  unpack = "tar --strip-components=1 -C #{daemon_dir} -xzf /tmp/#{daemon_version}.tar.gz"
  cd = "cd #{daemon_dir}"
  configure = "./configure"
  make = "make"
  install = "#{sudo_if_needed} make install"
  [wget, mkdir, unpack, cd, configure, make, install].join(' && ')
end

def heapster_dir
  "#{fetch(:shared_path)}/heapster"
end

def install_heapster
  on_rollback do
    run_locally("#{sudo_if_needed} rm -rf #{heapster_dir}")
    run_locally("#{sudo_if_needed} rm -f /tmp/heapster.tar.gz")
  end
  wget = wget_cmd(:file => "heapster.tar.gz", :local_file => "/tmp/heapster.tar.gz")
  mkdir = "#{sudo_if_needed} mkdir -p #{heapster_dir}"
  unpack = "#{sudo_if_needed} tar --strip-components=1 -C #{heapster_dir} -xzf /tmp/heapster.tar.gz"
  cd = "cd #{heapster_dir}"
  build = "#{sudo_if_needed} make"
  [wget, mkdir, unpack, cd, build, "true"].join(' && ')
end