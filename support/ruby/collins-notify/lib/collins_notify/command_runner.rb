module CollinsNotify
  class CommandRunner

    def run args
      app = CollinsNotify::Application.new

      begin
        app.configure! args
      rescue SystemExit => e
        exit e.status
      rescue CollinsNotify::ConfigurationError => e
        app.logger.fatal "Error with input option - #{e}"
        exit 1
      rescue StandardError => e
        app.logger.fatal "Error parsing CLI options - #{e}"
        $stdout.puts CollinsNotify::Options.get_instance(app.config)
        exit 2
      end

      unless app.valid? then
        $stdout.puts CollinsNotify::Options.get_instance(app.config)
        exit 3
      end

      cfg = app.config.to_hash.map{|k,v| "#{k}=#{v.inspect}"}.join(', ')
      app.logger.debug "Configuration -> #{cfg}"

      begin
        app.notify!
      rescue Timeout::Error => e
        app.logger.fatal "TIMEOUT sending notification via #{app.type}"
        exit 4
      rescue Exception => e
        app.logger.fatal "ERROR sending notification via #{app.type} - #{e}"
        exit 5
      end

      app.logger.info "Successfully sent #{app.type} notification"
      exit 0
    end

  end
end
