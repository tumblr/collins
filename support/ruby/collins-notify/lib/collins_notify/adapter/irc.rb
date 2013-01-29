require 'collins_notify/adapter/helper/carried-pigeon'

module CollinsNotify
  class IrcAdapter < Notifier
    register_name :irc
    supports_mimetype :text
    require_config :username, :host, :port

    include Collins::Util

    def configure!
      # An exception gets thrown if needed
      get_channel config.adapters[:irc], nil
      nil
    end

    # Available in template binding:
    #   message_obj - Depends on call
    #   nick - nickname
    #   channel - channel sending to
    #   host - host connecting to
    #   port - port on host being connected to
    def notify! to = nil, message_obj = OpenStruct.new
      tmp_config = symbolize_hash(deep_copy_hash(config.adapters[:irc]))
      host = tmp_config.delete(:host)
      port = tmp_config.delete(:port).to_i
      nick = tmp_config.delete(:username)
      channel = get_channel(tmp_config, to)
      tmp_config.delete(:channel)
      cp_config = {
        :host => host,
        :port => port,
        :nick => nick,
        :channel => channel,
        :logger => logger
      }
      cp_config.merge!(tmp_config)
      logger.trace "Using IRC config: #{cp_config.inspect}"
      cp = try_connect cp_config
      return false unless cp
      begin
        body = get_message_body(binding).gsub(/[\n\r]/, ' ')
        cp.message body, cp_config[:notice]
        true
      rescue CollinsNotify::CollinsNotifyException => e
        logger.error "error sending irc notification - #{e}"
        raise e
      rescue Exception => e
        logger.error "#{e.class.to_s} - error sending irc notification - #{e}"
        raise CollinsNotify::CollinsNotifyException.new e
      ensure
        cp.die
      end
    end

    protected
    def get_channel hash, to
      if config.recipient then
        make_channel config.recipient
      elsif to then
        make_channel to
      elsif hash[:channel] then
        make_channel hash.delete(:channel)
      else
        raise CollinsNotify::ConfigurationError.new "No irc.channel or config.recipient specified"
      end
    end

    def make_channel chan
      name = chan.start_with?('#') ? chan : "##{chan}"
      name.gsub(/[^A-Za-z0-9#]/, '_')
    end

    def try_connect config
      begin
        CarriedPigeon.new config
      rescue Exception => e
        logger.error "error connecting to server #{config[:host]} - #{e}"
        false
      end
    end

  end # class IrcAdapter

end # module CollinsNotify
