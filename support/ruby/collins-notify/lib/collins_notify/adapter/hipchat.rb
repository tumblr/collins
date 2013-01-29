require 'hipchat'

module CollinsNotify

  class HipchatAdapter < Notifier
    register_name :hipchat
    supports_mimetype :text
    require_config :api_token, :from_username # who to send the message as

    # Constants for coloring different messages
    module HipchatStatus
      SUCCESS = "green"
      WARNING = "yellow"
      ERROR   = "red"
      STATUS  = "purple"
      RANDOM  = "random"
    end

    def configure!
      @client = HipChat::Client.new api_token
      cfg_colors = hipchat_options.fetch(:colors, {})
      @colors = {
        :success => cfg_colors.fetch(:success, HipchatStatus::SUCCESS),
        :warning => cfg_colors.fetch(:warning, HipchatStatus::WARNING),
        :error   => cfg_colors.fetch(:error, HipchatStatus::ERROR),
        :status  => cfg_colors.fetch(:status, HipchatStatus::STATUS),
        :random  => cfg_colors.fetch(:random, HipchatStatus::RANDOM)
      }
      logger.info "Configured Hipchat client"
    end

    # Bindings available
    #   room_name - hipchat room
    #   username - user message being sent as
    #   at - If we are at'ing a person
    def notify! to = nil, message_obj = OpenStruct.new
      room_name = get_room message_obj
      username = get_from_username message_obj
      if room_name.nil? then
        raise CollinsNotify::CollinsNotifyException.new "No obj.room or hipchat.room specified"
      end
      message = get_message_body(binding).strip.gsub(/[\n\r]/, ' ')
      message = "@#{to} #{message}" unless to.nil?
      logger.info "Notifying #{room_name} from #{username} with message #{message}"
      if config.test? then
        logger.info "Not sending hipchat message in test mode"
        return true
      end
      opts = client_options(message_obj)
      logger.debug "Using hipchat options #{opts.inspect}"
      client[room_name].send(username, message, client_options(message_obj))
    end

    protected
    attr_accessor :client, :colors
    def api_token
      hipchat_options[:api_token]
    end
    def color mo
      if color = fetch_mo_option(mo, :color, false) then
        return color
      end
      [:success, :warning, :error, :status].each do |key|
        if fetch_mo_option(mo, key, false) then
          return colors[key]
        end
      end
      colors[:status]
    end
    def client_options mo
      opts = {}
      opts[:notify] = true if hipchat_options[:notify]
      opts[:color] = color(mo)
      opts
    end
    def get_from_username mo
      fetch_mo_option(mo, :from_username, hipchat_options[:from_username])
    end
    def hipchat_options
      @hipchat_options ||= symbolize_hash(deep_copy_hash(config.adapters[:hipchat]))
    end
    def get_room mo
      if config.recipient then
        config.recipient
      else
        fetch_mo_option(mo, :room, hipchat_options[:room])
      end
    end

  end

end
