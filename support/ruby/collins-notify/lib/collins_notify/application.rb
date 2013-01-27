module CollinsNotify
  class Application
    include CollinsNotify::ConfigurationMixin
    include Collins::Util::Logging

    attr_writer :config
    attr_reader :logger

    def initialize
      @logger = get_logger :logfile => $stderr, :program => 'collins_notify'
      @config = CollinsNotify::Configuration.new @logger
    end

    # Override ConfigMixin::#config
    def config
      @config
    end

    def configure! argv
      o = CollinsNotify::Options.get_instance config
      o.parse! argv
      if config.logfile then
        @logger = get_logger :logfile => config.logfile, :program => 'collins_notify'
      end
      @logger.level = config.severity
    end

    def notify! overrides = {}
      Timeout::timeout(config.timeout) do
        notifier.notify! overrides
      end
    end

    # Override ConfigurationMixin#valid?
    def valid?
      if config.valid? then
        begin
          @notifier = CollinsNotify::Notifier.get_adapter self
          @notifier.configure!
        rescue Exception => e
          logger.error "Error configuring #{type} notification - #{e}"
          pp e.backtrace
          return false
        end
        true
      else
        false
      end
    end

    protected
    attr_accessor :notifier

  end
end
