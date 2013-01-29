require 'erb'
require 'nokogiri'

module CollinsNotify

  class Notifier

    class << self
      def require_config *keys
        @requires_config = keys.map{|k| k.to_sym}
      end
      def requires_config?
        @requires_config && !@requires_config.empty?
      end
      def required_config_keys
        @requires_config || []
      end
      def register_name name
        @adapter_name = name
      end
      def adapter_name
        @adapter_name
      end
      def supports_mimetype *mimetypes
        @supported_mimetypes = mimetypes.map{|e| e.to_sym}
      end
      def mimetypes
        @supported_mimetypes || [:text]
      end
      def adapters
        ObjectSpace.each_object(Class).select { |klass| klass < self }.inject({}) do |ret,k|
          ret.update(k.adapter_name => k)
        end
      end
      def get_adapter application
        type = application.type
        config = application.config
        adapter_config = config.adapters[type]
        adapter = adapters[type]
        (raise CollinsNotifyException.new("Invalid notify type #{type}")) if adapter.nil?
        if adapter.requires_config? then
          (raise CollinsNotify::ConfigurationError.new "No config found for #{type}") if adapter_config.nil?
          adapter.required_config_keys.each do |key|
            unless adapter_config.key?(key) then
              raise CollinsNotify::ConfigurationError.new "Missing #{type}.#{key} config key"
            end
          end
        end
        (raise CollinsNotifyException.new("No config found for #{type}")) if adapter.requires_config? and adapter_config.nil?
        adapter.new application
      end
    end

    def initialize app
      @config = app.config
      @logger = app.logger
    end

    # Throw an exception if it cant be configured
    def configure!
      raise NotImplementedError.new "CollinsNotify::Notifier#configure! must be implemented"
    end

    # Return boolean indicating success/fail
    def notify! to = nil, message_obj = OpenStruct.new
      raise NotImplementedError.new "CollinsNotify::Notifier#notify! must be implemented"
    end

    def supports_text?
      self.class.mimetypes.include?(:text)
    end
    def supports_html?
      self.class.mimetypes.include?(:html)
    end

    protected
    attr_reader :config
    attr_reader :logger

    # b is the binding to use
    def get_message_body b
      if config.stdin? then
        message_txt = $stdin.read.strip
        if config.template_processor == :erb then
          render_template message_txt, b
        else
          message_txt
        end
      elsif config.template? then
        tmpl = config.resolved_template
        logger.debug "Using template file #{tmpl}"
        render_template File.new(tmpl), b
      else
        raise CollinsNotify::CollinsNotifyException.new "Unknown message body type"
      end
    end

    def render_template tmpl, b
      template_format = config.template_format
      if template_format == :default && tmpl.is_a?(File) && tmpl.path.include?(".html") then
        logger.info "Detected HTML formatted template file"
        # If format was unspecified but it seems like it's html, make it so
        template_format = :html
      end
      tmpl_txt = tmpl.is_a?(File) ? tmpl.read : tmpl
      begin
        template = ERB.new(tmpl_txt, nil, '<>')
        rendered_tmpl = plain_text = template.result(b)
        if template_format == :html then
          logger.debug "Template format is html, rendering it as plain text"
          plain_text = as_plain_text rendered_tmpl
        end
        if supports_html? then
          logger.debug "HTML is supported by #{self.class.adapter_name}"
          handle_html b, rendered_tmpl, plain_text
        else
          logger.debug "Only plain text supported by #{self.class.adapter_name}"
          logger.debug "Rendered plain text: '#{plain_text.gsub(/[\r\n]/, ' ')}'"
          plain_text
        end
      rescue Exception => e
        raise CollinsNotify::CollinsNotifyException.new "Invalid template #{tmpl} - #{e}"
      end
    end

    # b is original binding, html is html version, plain_text is plain text version
    def handle_html b, html, plain_text
      html
    end

    def as_plain_text html
      Nokogiri::HTML(html).text
    end

  end

end
