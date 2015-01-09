require 'json'

module CollinsNotify

  # currently this only supports incoming webhooks to pust to slack
  # https://tumblr.slack.com/services/new/incoming-webhook

  class SlackAdapter < Notifier
    register_name :slack
    supports_mimetype :text
    require_config :webhook_url

    def configure!
      @webhook_url = config.adapters[:slack][:webhook_url]
      logger.info "Configured Slack adapter"
    end
    
    # Available in template binding:
    # message_obj - Depends on call
    # channel - channel sending to
    def notify! message_obj = OpenStruct.new, to = nil
      slack_hash = Hash.new
      channel = get_channel config.adapters[:slack], to
      slack_hash['channel'] = channel
      optional_parameters = [:username, :icon_url, :icon_emoji]
      optional_parameters.each do |op|
        if config.adapters[:slack][op]
          slack_hash[op] = config.adapters[:slack][op]
        end
      end

      slack_hash['text'] = get_message_body(binding)

      @logger.debug "slack parameters: #{slack_hash.inspect}"
        
      if config.test? then
        @logger.info "Not sending message in test mode"
        return true
      end

      begin
        @logger.debug "Posting to slack webhook: #{@webhook_url}"
        reply = HTTParty.post(@webhook_url, :body => JSON.dump(slack_hash))
        reply.response.value # this raises an error if the response said it was unsuccessful
        true
      rescue CollinsNotify::CollinsNotifyException => e
        @logger.error "error sending slack notification - #{e}"
        raise e
      rescue Exception => e
        @logger.error "#{e.class.to_s} - error sending slack notification - #{e}"
        raise CollinsNotify::CollinsNotifyException.new e
      end
    end

    protected

    # get the channel from one of many sources
    # slack does not require a channel for a web hook (you can set a default through the webhook settings)
    # but we're going to require that a channel is specified for the purposes of this gem
    def get_channel hash, to
      if config.recipient then
        make_channel config.recipient
      elsif to then
        make_channel to
      elsif hash[:channel]
        make_channel hash[:channel]
      else
        raise CollinsNotify::ConfigurationError.new "No slack.channel or config.recipient specified"
      end
    end

    # slack can both DM users and post to channels
    # If the recipient isn't explicitly a user (@username) then we'll assume the string is a channel
    def make_channel chan
      @logger.debug "In make_channel, got #{chan}"
      # If we are DMing a user (chan looks like @username), do nothing
      # otherwise, make sure chan starts with a '#', if it doesn't already

      # channel names must be lowercase, and not contain spaces or periods
      # I'm going to assume that usernames need the same requirements
      chan.sub(/^(?!#|@)/,'#').downcase.gsub(/ |\./, '_')
    end
  end
end
