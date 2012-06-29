Capistrano::Configuration.instance(:must_exist).load do

  ENVIRONMENT = :environment

  def get_environments
    namespaces[ENVIRONMENT].tasks.reject{|k,v| v.desc.class == NilClass}.keys.map{|name| "environment:#{name}"}
  end

  role :server, ""

  namespace ENVIRONMENT do
    desc "Func Localhost"
    task :func do
      set ENVIRONMENT, "func"
      set :use_sudo, true
    end

    task :check do
      env = fetch(ENVIRONMENT, "")
      if env.empty? then
        func
      else
        #logger.trace("Already have environment specified: #{env}")
      end
    end
  end

  desc "List available environments"
  task :listenvs do
    puts "Environments"
    tsks = namespaces[ENVIRONMENT].tasks
    tsks.each { |name,task| puts("\tenvironment:#{name.to_s} - #{task.desc.rstrip}") if task.desc.class != NilClass }
    abort
  end

end
