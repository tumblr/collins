Capistrano::Configuration.instance(:must_exist).load do

  namespace :deploy do

    desc <<-DESC
      Publishs your project into the central repository
    DESC
    task :publish do
      strategy.handle_local_upload
    end

    desc <<-DESC
      Deploys your project. Handy wrapper to hook into the beginning of the deployment. Note that \
      this will generally only work for applications that have already been deployed \
      once. For a "cold" deploy, you'll want to take a look at the `deploy:cold' \
      task, which handles the cold start specifically. This task will restart the server.
    DESC
    task :default do
      update
      restart
    end

    desc <<-DESC
      Deploys and starts a `cold' application. This is useful if you have not \
      deployed your application before, or if your application is (for some \
      other reason) not currently running. It will deploy the code, run any \
      pending migrations, and then instead of invoking `deploy:restart', it will \
      invoke `deploy:start' to fire up the application servers.
    DESC
    task :cold do
      setup
      check
      update
      start
    end

    desc "Starts an application that is not yet running."
    task :start do
      run_locally("#{init_cmd('start')}")
    end

    desc "Stops an already running application."
    task :stop do
      run_locally("#{init_cmd('stop')}")
    end

    desc "Restart an application."
    task :restart do
      run_locally("#{init_cmd('restart')}")
    end

    desc "Check the status of the application"
    task :status do
      run_locally("#{init_cmd('status')}")
    end

    desc <<-DESC
      Uploads '#{File.expand_path(File.dirname(__FILE__) + "/../")}/*' to the currently deployed version. \
      This is useful for updating configuration without redeploying. This task does not \
      restart the service, and changes will not be picked up until the restart task is called.
    DESC
    task :configs do
      top_dir = File.expand_path("#{File.dirname(__FILE__)}/../../")
      cfg_dir = File.join(top_dir, "config", "")
      files = Dir["#{cfg_dir}**/*"]

      ENV["FILES"] = files.join(",")
      upload
    end # task

    desc <<-DESC
      Test deployment dependencies. Checks things like directory permissions, \
      necessary utilities, and so forth, reporting on the things that appear to \
      be incorrect or missing. This is good for making sure a deploy has a \
      chance of working before you actually run `cap deploy'.

      You can define your own dependencies, as well, using the `depend' method:

        depend :remote, :gem, "tzinfo", ">=0.3.3"
        depend :local, :command, "svn"
        depend :remote, :directory, "/u/depot/files"
    DESC
    task :check, :except => { :no_release => true } do
      dependencies = strategy.check!
      other = fetch(:dependencies, {})
      other.each do |location, types|
       types.each do |type, calls|
         if type == :gem
           dependencies.send(location).command(fetch(:gem_command, "gem")).or("`gem' command could not be found. Try setting :gem_command")
         end

         calls.each do |args|
           if args.length > 1 && args.last.class == Hash then
             dependencies.send(location).send(type, *args)
           end
         end
       end
      end

      if dependencies.pass?
      	puts "You appear to have all necessary dependencies installed"
      else
      	puts "The following dependencies failed: "
      	dependencies.reject { |d| d.pass? }.each do |d|
      	  puts "--> #{d.message}"
      	end
      	puts "Trying to fix dependencies, rerun check after this has completed"
      	setup_fixable_dependencies
      	abort
      end
    end

    desc <<-DESC
      Prepares one or more servers for deployment. Before you can use any \
      of the Capistrano deployment tasks with your project, you will need to \
      make sure all of your servers have been prepared with `cap deploy:setup'. When \
      you add a new server to your cluster, you can easily run the setup task \
      on just that server by specifying the HOSTS environment variable:

        $ cap HOSTS=new.server.com ROLES=server deploy:setup
        $ cap HOSTS=new.server.com,new2.server.com ROLES=server deploy:setup

      It is safe to run this task on servers that have already been set up; it \
      will not destroy any deployed revisions or data.
    DESC
    task :setup, :except => { :no_release => true } do
      dt = fetch(:deploy_to)
      rp = fetch(:releases_path)
      sp = fetch(:shared_path)
      dirs = [dt, rp, sp]
      dirs += shared_children.map { |d| File.join(sp, d) }
      run_locally("#{sudo_if_needed} mkdir -p #{dirs.join(' ')} && #{sudo_if_needed} chmod g+w #{dirs.join(' ')}")
      setup_fixable_dependencies
    end

    desc <<-DESC
      Copies your project and updates the symlink. It does this in a \
      transaction, so that if either `update_code' or `symlink' fail, all \
      changes made to the remote servers will be rolled back, leaving your \
      system in the same state it was in before `update' was invoked. Usually, \
      you will want to call `deploy' instead of `update', but `update' can be \
      handy if you want to deploy, but not immediately restart your application.
    DESC
    task :update do
      transaction do
      	update_code
      	symlink
      end
    end

    desc <<-DESC
      Copies your project to the remote servers. This is the first stage \
      of any deployment; moving your updated code and assets to the deployment \
      servers. You will rarely call this task directly, however; instead, you \
      should call the `deploy' task (to do a complete deploy) or the `update' \
      task (if you want to perform the `restart' task separately).

      You will need to make sure you set the :scm variable to the source \
      control software you are using (it defaults to :subversion), and the \
      :deploy_via variable to the strategy you want to use to deploy (it \
      defaults to :checkout).
    DESC
    task :update_code do
      on_rollback { run("#{sudo_if_needed} rm -rf #{fetch(:release_path)}; true") }
      strategy.deploy!
      finalize_update
    end

    desc <<-DESC
      Updates the symlink to the most recently deployed version. Capistrano works \
      by putting each new release of your application in its own directory. When \
      you deploy a new version, this task's job is to update the `current' symlink \
      to point at the new version. You will rarely need to call this task \
      directly; instead, use the `deploy' task (which performs a complete \
      deploy, including `restart') or the 'update' task (which does everything \
      except `restart').
    DESC
    task :symlink, :except => {:no_release => true} do
      on_rollback do
        pr = previous_release
        if pr
          run_locally("#{sudo_if_needed} rm -f #{fetch(:current_path)}; #{sudo_if_needed} ln -s #{fetch(:previous_release)} #{fetch(:current_path)}; true")
        else
          logger.important "no previous release to rollback to, rollback of symlink skipped"
        end
      end
      run_locally("#{sudo_if_needed} rm -f #{fetch(:current_path)} && #{sudo_if_needed} ln -s #{latest_release} #{fetch(:current_path)}")
    end

    desc <<-DESC
      Clean up old releases. By default, the last 5 releases are kept on each \
      server (though you can change this with the keep_releases variable). All \
      other deployed revisions are removed from the servers. By default, this \
      will use sudo to clean up the old releases, but if sudo is not available \
      for your environment, set the :use_sudo variable to false instead.
    DESC
    task :cleanup, :except => { :no_release => true } do
      count = fetch(:keep_releases, 5).to_i
      rel = releases
      if count >= rel.length
      	logger.important "no old releases to clean up"
      else
      	logger.info "keeping #{count} of #{rel.length} deployed releases"

      	directories = (rel - rel.last(count)).map { |release| File.join(fetch(:releases_path), release) }.join(" ")
      	run_locally("#{sudo_if_needed} rm -rf #{directories}")
      end
    end

    desc <<-DESC
      Copy files to the currently deployed version. This is useful for updating \
      files piecemeal, such as when you need to quickly deploy only a single \
      file. Some files, such as updated templates, images, or stylesheets, \
      might not require a full deploy, and especially in emergency situations \
      it can be handy to just push the updates to production, quickly.

      To use this task, specify the files and directories you want to copy as a \
      comma-delimited list in the FILES environment variable. All directories \
      will be processed recursively, with all files being pushed to the \
      deployment servers.

        $ cap deploy:upload FILES=templates,controller.rb

      Dir globs are also supported:

        $ cap deploy:upload FILES='config/*.scala'
    DESC
    task :upload, :except => { :no_release => true } do
      files = (ENV["FILES"] || "").split(",").map { |f| Dir[f.strip] }.flatten
      abort "Please specify at least one file or directory to update (via the FILES environment variable)" if files.empty?
      project_root = File.join(File.expand_path("#{File.dirname(__FILE__)}/../../"), "")
      app = fetch(:application)
      remote_tmpdir = File.join("/tmp", "#{app}-config-#{rand(10)}")
      seen_dirs = {}
      transaction do
      	on_rollback do
      	  run_locally("#{sudo_if_needed} rm -rf #{remote_tmpdir}")
      	end
      	run_locally("mkdir #{remote_tmpdir}")
      	files.each do |file|
      	  relative_fpath = file.sub(project_root, "") # e.g. config/foo.scala
      	  relative_fdir = File.dirname(file).sub(project_root, "") # e.g. config
      	  filename = File.basename(file) # e.g. foo.scala

      	  remote_tmpfile = "#{remote_tmpdir}/#{filename}" # /tmp/x/foo.scala
      	  remote_dstdir = "#{fetch(:current_path)}/#{relative_fdir}" # /usr/local/x/config
      	  remote_dstfile = "#{remote_dstdir}/#{filename}" # /usr/local/x/config/foo.scala

      	  if not seen_dirs.has_key?(remote_dstdir) then
      	    run_locally("#{sudo_if_needed} mkdir -p #{remote_dstdir}") # create /usr/local/x/config
      	    seen_dirs[remote_dstdir] = true
      	  end
      	  if not File.directory?(file) then
      	    top.upload(file, remote_tmpfile, :via => :scp) # scp config/foo.scala to /tmp/x/foo.scala
      	    run_locally("#{sudo_if_needed} mv #{remote_tmpfile} #{remote_dstfile}") # mv /tmp/foo.scala /usr/..
      	  end
      	end
      	run_locally("#{sudo_if_needed} rm -rf #{remote_tmpdir}") # cleanup
      end # transaction
    end

    namespace :rollback do
      desc <<-DESC
        [internal] Points the current symlink at the previous revision.
        This is called by the rollback sequence, and should rarely (if
        ever) need to be called directly.
      DESC
      task :revision, :except => { :no_release => true } do
        rel = previous_release
        if rel
          run_locally("#{sudo_if_needed} rm #{fetch(:current_path)}; #{sudo_if_needed} ln -s #{rel} #{fetch(:current_path)}")
        else
          abort "could not rollback the code because there is no prior release"
        end
      end

      desc <<-DESC
        [internal] Removes the most recently deployed release.
        This is called by the rollback sequence, and should rarely
        (if ever) need to be called directly.
      DESC
      task :cleanup, :except => { :no_release => true } do
        cr = current_release
        run_locally("if [ `readlink #{fetch(:current_path)}` != #{cr} ]; then #{sudo_if_needed} rm -rf #{cr}; fi")
      end

      desc <<-DESC
        Rolls back to the previously deployed version. The `current' symlink will \
        be updated to point at the previously deployed version, and then the \
        current release will be removed from the servers.
      DESC
      task :code, :except => { :no_release => true } do
      	revision
      	cleanup
      end

      desc <<-DESC
        Rolls back to a previous version and restarts. This is handy if you ever \
        discover that you've deployed a lemon; `cap rollback' and you're right \
        back where you were, on the previously deployed version.
      DESC
      task :default do
      	revision
      	cleanup
      end
    end

    namespace :pending do
      task :diff do
        logger.important("Not implemented")
        abort
      end
      task :default do
        logger.important("Not implemented")
        abort
      end
    end

    def finalize_update
      lr = latest_release
      run_locally("#{sudo_if_needed} chmod -R g+w #{lr}") if fetch(:group_writable, true)
      run_locally("#{sudo_if_needed} chmod +x #{init_script(lr)}")
    end

    def setup_fixable_dependencies
      deps = fetch(:dependencies, {})
      # location :remote/:local, type :directory/:writable, args is else
      deps.each do |location, types|
        types.each do |type, calls|
          calls.each do |args|
            if args.length > 1 && args.last.class == Hash then
              args = args.last
              if args.has_key?(:fix_command) then
                run_locally("#{sudo_if_needed} #{args[:fix_command]}")
              end
            end
          end
        end
      end
    end

  end # End of namespace

  def init_script(path = fetch(:current_path))
    path + "/scripts/" + fetch(:application) + ".sh"
  end
  def init_cmd(cmd)
    init_script + " #{cmd}"
  end

  def releases
    capture("ls -xt #{fetch(:releases_path)}").split.reverse
  end
  # Used for current_release, previous_release, and indirectly by latest_release

  def current_release
    File.join(fetch(:releases_path), releases.last)
  end
  # Used by latest_release, rollback:cleanup, 

  def previous_release
    rels = releases
    if rels.length > 1 then
      File.join(fetch(:releases_path), rels[-2])
    else
      nil
    end
  end
  # Used by symlink (rollback), rollback:revision

  def latest_release
    exists?("deploy_timestamped".intern) ? fetch(:release_path) : current_release
  end
  # Used by finalize_update, symlink, 

  def release_name
    set "deploy_timestamped".intern, true
    Time.now.utc.strftime("%Y%m%d%H%M%S")
  end

end
