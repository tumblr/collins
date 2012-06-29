require 'capistrano/recipes/deploy/strategy/copy'
require 'fileutils'
require 'tempfile'  # Dir.tmpdir

# Generate a string for a wget command. Required options:
#   :local_file - string - Where the file should be put
# One of:
#   :remote_file - string - URL of file
#   :file - string - Filename as relative URL on http://10.60.26.92:8888/
require 'uri'
class Capistrano::Deploy::LocalDependency
  def sudo_test(command)
    if test("[ `whoami` = root ]")
      test(command)
    else
      test("sudo -p 'sudo password: ' #{command}")  
    end
  end

  def test(command)
    success = system(command)
    self
  end

  def directory(dir)
    sudo_test("test -d #{dir}")
  end

  def writable(path)
    sudo_test("test -w #{path}")
  end

  def readable(path)
    sudo_test("test -r #{path}")
  end
end

def wget_cmd(options)
  raise Exception.new("local_file not specified") unless options.has_key?(:local_file)
  options[:protocol] = "http" unless options.has_key?(:protocol)
  options[:host] = fetch(:upload_host) unless options.has_key?(:host)
  options[:port] = 8888 unless options[:port]
  if (options.has_key?(:remote_file) && options[:remote_file].class == String) then
    options[:remote_file] = URI::parse(options[:remote_file])
  elsif (options.has_key?(:remote_file)) then
    raise Exception.new("remote_file specified but is not a string")
  elsif (options.has_key?(:file)) then
    options[:remote_file] = URI::parse("#{options[:protocol]}://#{options[:host]}:#{options[:port]}/#{options[:file]}")
  else
    raise Exception.new("Neither remote_file or file were specified as options")
  end
  "wget -q -O #{options[:local_file]} #{options[:remote_file].to_s}"
end

module Capistrano
  module Deploy
    module Strategy

      class CustomCopy < Copy

        def check!
          Dependencies.new(configuration) do |d|
            rp = fetch(:releases_path)
            dt = fetch(:deploy_to)

            d.local.directory(rp).or("`#{rp}' does not exist. Please run `cap deploy:setup'.")
            d.local.writable(dt).or("You do not have permissions to write to `#{dt}'.")
            d.local.writable(rp).or("You do not have permissions to write to `#{rp}'.")

            d.local.command(source.local.command) if source.local.command
            d.local.command(compress(nil, nil).first)
            d.local.command(decompress(nil).first)

            ["log","run"].each do |dir|
              ddir = "/var/#{dir}/#{fetch(:application)}"

              unless d.local.directory(ddir) and d.local.writable(ddir)
                run_locally("#{sudo_if_needed} mkdir -p #{ddir}")
              end
            end
          end
        end

        def deploy!
          distribute!
        end

        def handle_local_upload
          latest_f = filename
          remote_file = remote_upload_dst(latest_f)
          zip = latest_remote_filename
          zip_link = File.join(File.dirname(remote_file), zip)
          args = {:hosts => fetch(:upload_host)}
          run "if [[ ! -f #{remote_file} || ! -f #{zip_link} ]]; then echo do_upload; fi", args do |c,s,d|
            if d.include?("do_upload") then
              logger.important("Uploading local file to remote download server")
              copy_latest(latest_f, zip_link)
              run("#{use_sudo} chmod -R 777 #{File.dirname(remote_file)}", args)
            end
          end
        end

        private
        def remote_upload_dst(local_f)
          "/usr/local/static_file_server/#{File.basename(local_f)}"
        end
        def latest_remote_filename
          "#{fetch(:application)}.zip"
        end
        def copy_latest(local_file, symlink_dest)
          args = {
            :via => :scp,
            :hosts => fetch(:upload_host)
          }
          rd = remote_upload_dst(local_file)
          upload(local_file, rd, args)
          run("rm -f #{symlink_dest}", {:hosts => fetch(:upload_host)})
          run("ln -s #{rd} #{symlink_dest}", {:hosts => fetch(:upload_host)})
        end

        def filename
          repo = fetch(:repository, "")
          if repo == "" then
            logger.important("repository is empty, exiting")
            abort
          end
          file = Dir["#{repo}/*.zip"].sort {|file1, file2| File.ctime(file1) <=> File.ctime(file2)}.last
          if file == nil then
            logger.important("no zip file found in #{repo}")
            abort
          end
          file
        end

        # copy this working directory into the releases directory
        def distribute!
          rp = fetch(:release_path)
          dp = File.join(File.dirname(__FILE__), '../../..')
          run_locally "#{sudo_if_needed} mkdir -p #{rp}"
          run_locally "#{sudo_if_needed} cp -rv #{dp} #{rp}"
        end
      end
    end
  end
end

