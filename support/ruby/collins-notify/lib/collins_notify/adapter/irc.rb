require 'carrier-pigeon'

module CollinsNotify
  class IrcAdapter < Notifier
    register_name :irc
    supports_mimetype :text
    require_config :username, :host, :port

    include Collins::Util

    def configure!
      # An exception gets thrown if needed
      get_channel config.adapters[:irc]
      nil
    end

    def notify! overrides = {}
      tmp_config = symbolize_hash(deep_copy_hash(config.adapters[:irc]).merge(overrides))
      pp tmp_config
      cp_config = {
        :host => tmp_config.delete(:host),
        :port => tmp_config.delete(:port).to_i,
        :nick => tmp_config.delete(:username),
        :channel => get_channel(tmp_config),
      }
      tmp_config.delete(:channel)
      cp_config.merge!(tmp_config)
      cp = CarrierPigeon.new cp_config
      begin
        cp.message cp_config[:channel], get_message_body, cp_config[:notice]
      ensure
        cp.die
      end
    end

    protected
    def get_channel hash
      if config.recipient then
        make_channel config.recipient
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
  end
end
