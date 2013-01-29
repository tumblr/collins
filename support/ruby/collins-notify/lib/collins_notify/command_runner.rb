module CollinsNotify
  class CommandRunner

    def run args
      app = CollinsNotify::Application.new

      begin
        app.configure! args
        get_contact_and_asset app, true # throws exception if not configured properly
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
      # This is legacy behavior
      app.config.type = :email if app.config.type.nil?

      unless app.valid? then
        $stdout.puts CollinsNotify::Options.get_instance(app.config)
        exit 3
      end

      app.logger.trace "Configuration -> #{app.config.to_hash.inspect}"

      begin
        contact, asset = get_contact_and_asset app
        if !app.notify!(contact, asset) then
          raise Exception.new "failed"
        end
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

    protected
    def get_contact_and_asset app, test_only = false
      return [nil, OpenStruct.new] unless app.selector and !app.selector.empty?
      ccfg = app.config.collins
      if ccfg.nil? or ccfg.empty? then
        raise CollinsNotify::ConfigurationError.new "No collins configuration but selector specified"
      end
      collins = Collins::Client.new ccfg.merge(:logger => app.logger)
      return if test_only
      asset = nil
      if app.selector[:tag] then
        app.logger.info "Finding collins asset using asset tag #{app.selector[:tag]}"
        asset = collins.get app.selector[:tag]
      else
        app.logger.info "Finding collins asset using asset selector #{app.selector.inspect}"
        assets = collins.find(app.selector)
        app.logger.debug "Found #{assets.size} assets in collins using selector, only using first"
        asset = assets.first
      end
      if asset.nil? then
        raise CollinsNotify::ConfigurationError.new "No asset found using #{app.selector.inspect}"
      end
      [asset.contact, asset]
    end

  end
end
