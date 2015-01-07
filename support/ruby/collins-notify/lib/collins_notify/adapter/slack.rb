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
    def notify! message_obj = OpenStruct.new, to = nil
      slack_hash = Hash.new
      slack_hash['text'] = get_message_body(binding)
      slack_hash['channel'] = get_channel config.adapters[:slack], to
      @logger.debug "Posting to slack webhook: #{@webhook_url}"
      HTTParty.post(@webhook_url, :body => JSON.dump(slack_hash))
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
      # see if we're DMing a user, or posting to a channel
      chan_type_identifier = chan.start_with?('@') ? '@' : '#'
      # ensure the channel name starts with the right character
      chan = chan.start_with?(chan_type_identifier) ? chan : "##{chan}"
      # I can't find any specs on what characters are valid for a slack channel name or user name
      # if we ever find that there are invalid characters, they will need to be removed here
      return chan
    end
  end
end
