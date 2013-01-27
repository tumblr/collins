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

    def configure!
      raise NotImplementedError.new "CollinsNotify::Notifier#configure! must be implemented"
    end

    def notify! overrides = {}
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

    def get_message_body
      if config.stdin? then
        $stdin.read.strip
      elsif config.template? then
        tmpl = config.resolved_template
        # do stuff
        "not yet implemented"
      else
        raise CollinsNotify::CollinsNotifyException.new "Unknown message body type"
      end
    end

  end

end
