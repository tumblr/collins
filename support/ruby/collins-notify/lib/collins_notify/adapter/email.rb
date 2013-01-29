require 'mail'

module CollinsNotify
  class EmailAdapter < Notifier
    register_name :email
    supports_mimetype :text, :html
    require_config

    def configure!
      mail_cfg = mail_options
      del_meth = mail_cfg.fetch(:delivery_method, :smtp)
      logger.info "Configuring email delivery method #{del_meth}"
      logger.debug "Using email config values #{mail_cfg.inspect}"
      Mail.defaults do
        delivery_method del_meth, mail_cfg
      end
      nil
    end

    # Available in template binding
    #   sender_address - Address of sender
    #   recipient_address - Resolved address of recipient
    #   subject - Mail subject
    #   message_obj - Depends on call
    def notify! to = nil, message_obj = OpenStruct.new
      sender_address = get_sender_address message_obj
      recipient_address = get_recipient_address message_obj, to
      subject = get_subject message_obj
      message = get_message_body(binding)
      subject = message.subject # Subject may change based on message template
      (raise CollinsNotify::CollinsNotifyException.new("No subject specified")) if subject.nil?
      logger.info "From: #{sender_address}, To: #{recipient_address}, Subject: #{subject}"
      logger.trace "Message body:\n#{message}"
      begin
        mail = Mail.new message
        logger.debug "Attempting to deliver email message"
        if config.test? then
          logger.info "Not delivering email, running in test mode"
        else
          logger.info "Delivering email message"
          mail.deliver!
        end
        true
      rescue Exception => e
        logger.fatal "Error sending email - #{e}"
        false
      end
    end

    protected
    def get_recipient_address mo, to
      if config.recipient then
        make_address config.recipient
      elsif to then
        make_address to
      elsif fetch_mo_option(mo, :recipient, nil) then
        make_address fetch_mo_option(mo, :recipient, nil)
      else
        raise CollinsNotify::ConfigurationError.new "No email.recipient or config.recipient specified"
      end
    end

    def get_sender_address mo
      default = mail_options.fetch(:sender_address, "Notifier <notifier@example.com>")
      fetch_mo_option(mo, :sender_address, default)
    end

    def get_subject mo
      default = mail_options.fetch(:subject, nil)
      fetch_mo_option(mo, :subject, default)
    end

    def handle_html b, html, plain_text
      is_html = !html.nil?
      message = Mail::Message.new plain_text
      if is_html then
        message.body = ""
        message.html_part = Mail::Part.new do
          content_type 'text/html; charset=UTF-8'
          body html
        end
        message.text_part = Mail::Part.new do
          body plain_text
        end
      end
      message[:from] = fetch_bound_option(b, "sender_address", "NOOP") unless message.from
      message[:to] = fetch_bound_option(b, "recipient_address", "NOOP") unless message.to
      message[:subject] = fetch_bound_option(b, "subject", "Collins Notifier") unless message.subject
      message
    end

    def mail_options
      @mail_options ||= symbolize_hash(deep_copy_hash(config.adapters[:email]))
    end

    def make_address address
      if address.include?("@") then
        return address
      end
      domain = "example.com"
      if mail_options[:domain] then
        domain = mail_options[:domain]
      end
      "#{address}@#{domain}"
    end

  end
end
