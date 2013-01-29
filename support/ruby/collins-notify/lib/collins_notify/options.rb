require 'optparse'
require 'yaml'

module CollinsNotify

  class Options < OptionParser
    ETC_CONFIG = "/etc/collins_notify.yaml"

    include Collins::Util

    def self.app_name
      File.basename($0)
    end
    def self.get_instance config
      i = CollinsNotify::Options.new config
      i.banner = "Usage: #{app_name} --config=CONFIG [options]"
      i.separator ""
      i.instance_eval { setup }
      i
    end

    def parse! argv = default_argv
      res = super
      cfg_file = get_config_file
      if cfg_file then #config.config_file then
        yaml = YAML::load(File.open(cfg_file))
        adapters = CollinsNotify::Notifier.adapters.keys
        yaml.each do |k,v|
          if adapters.include?(k.to_sym) then
            config.adapters[k.to_sym] = symbolize_hash(v)
          else
            try_configure k, v
          end
        end
      end
      res
    end

    protected
    attr_reader :config
    def initialize config
      @config = config
      super()
    end

    def get_config_file
      if config.config_file then
        config.config_file
      elsif File.exists?(ETC_CONFIG) && File.readable?(ETC_CONFIG) then
        ETC_CONFIG
      else
        nil
      end
    end

    def setup
      separator "Specific options:"
      on('-c', '--config=CONFIG', 'Notifier config. Must include any neccesary credentials for the specified notify type') do |config|
        @config.config_file = config
      end
      on('--recipient=NAME', 'Email address, username, channel, etc') do |n|
        @config.recipient = n
      end
      on('--selector=SELECTOR', 'Selector to use to retrieve collins assets') do |s|
        @config.selector = eval(s)
      end
      on('--tag=TAG', 'Unique tag of asset') do |t|
        @config.selector = {:tag => t}
      end
      adapters = CollinsNotify::Notifier.adapters.keys
      msg = "Select notification type (#{adapters.sort.join(', ')})"
      on('--type=TYPE', adapters, msg) do |t|
        @config.type = t
      end
      separator ""
      separator "Common options:"
      on_tail('-d', '--debug', 'Legacy, same as -v') do
        @config.increase_verbosity
      end
      on_tail('-h', '--help', 'This help') do
        $stdout.puts self
        exit 0
      end
      on_tail('--logfile=FILE', 'Log file to use, or stderr') do |file|
        @config.logfile = file
      end
      on_tail('--template=TEMPLATE', 'Template to use for notifications, erb file') do |t|
        @config.template = t
      end
      on_tail('--template-dir=DIR', 'Use fully qualified path in --template, do not use this') do |d|
        @config.template_dir = d
      end
      on_tail('--template-format=FMT', [:default, :html], 'Template format (default, html).') do |t|
        @config.template_format = t.to_sym
      end
      on_tail('--template-processor=PROC', [:default, :erb], 'Template processor (default, erb).') do |t|
        @config.template_processor = t.to_sym
      end
      on_tail('-t', '--[no-]test', 'Enable or disable testing') do |t|
        @config.test = t
      end
      on_tail('--timeout=TIMEOUT', Integer, 'Timeout in seconds, defaults to 10') do |t|
        @config.timeout = t
      end
      on_tail('-v', '--verbose', verbose_help) do
        @config.increase_verbosity
      end
      on_tail('-V', '--version', 'Software version') do
        $stdout.puts "collins_notify #{CollinsNotify::Version}"
        exit 0
      end
    end
    def verbose_help
      "Increase verbosity. Specify multiple -v options to increase (up to 4)"
    end

    private
    def try_configure key, value
      begin
        config.send("#{key}=".to_sym, value)
      rescue Exception => e
        raise CollinsNotify::ConfigurationError.new "#{key} is not a valid config option"
      end
    end

  end

end
