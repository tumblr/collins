require 'capistrano/recipes/deploy/strategy/copy'

module Capistrano
  module Deploy
    module Strategy
      class Wget < Copy
        def deploy!
          if wget_local? then
            system "cp target/collins.zip /tmp"
          else
            run "#{wget_path} #{source_url} -q -O #{remote_filename}"
          end
          decompress_remote_file
        end
        def source_url
          configuration[:source_url] || (raise Exception.new("no source_url specified"))
        end
        def wget_local?
          configuration[:wget_local] || false
        end
        def wget_path
          @wget_path ||= configuration[:wget_path] || "/usr/bin/wget"
        end
        def remote_filename
          @remote_filename ||= File.join(remote_dir, "collins.zip")
        end
        def compression
          Compression.new("zip",     %w(zip -qyr), %w(unzip -q))
        end
        def decompress_remote_file
          run "cd #{configuration[:releases_path]} && #{decompress(remote_filename).join(" ")} && rm #{remote_filename}"
          run "mv #{fetch(:releases_path)}/collins #{fetch(:release_path)}"
        end
      end
    end
  end
end
