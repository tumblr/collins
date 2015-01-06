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
      @logger.debug "Posting to slack webhook: #{@webhook_url}"
      HTTParty.post(@webhook_url, :body => JSON.dump(slack_hash))
    end
  end
end
