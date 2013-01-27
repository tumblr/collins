module CollinsNotify

  class Configuration
    include CollinsNotify::ConfigurationMixin

    attr_reader :logger
    attr_accessor :adapters

    def initialize logger, opts = {}
      @adapters = {}
      configurable.each do |key, value|
        instance_variable_set "@#{key.to_s}".to_sym, value
      end
      @logger = logger
      merge opts
    end

    # Override ConfigurationMixin#config
    def config
      self
    end

    # Override ConfigurationMixin#valid?
    def valid?
      adapters = CollinsNotify::Notifier.adapters.keys
      if type.nil? || !adapters.include?(type) then
        msg = "Invalid notifier type specified. Valid notifiers: #{adapters.sort.join(', ')}"
        logger.error msg
        return false
      end
      unless message_body? then
        logger.error "No message body found in specified template or stdin"
        return false
      end
      true
    end

    # Shorthand for severity assignment but silently fails if too verbose
    def increase_verbosity
      if severity > -1 then
        self.severity -= 1
      end
    end

    def message_body?
      template? || stdin?
    end

    def resolved_template
      return nil unless template
      if File.exists?(File.expand_path(template)) then
        return File.expand_path(template)
      elsif template_dir && File.exists?(File.expand_path(File.join(template_dir, template))) then
        return File.expand_path(File.join(template_dir, template))
      elsif File.exists?(File.expand_path(File.join(default_template_dir, template))) then
        return File.expand_path(File.join(default_template_dir, template))
      end
    end

    def stdin?
      !$stdin.tty?
    end

    def template?
      !resolved_template.nil?
    end

    alias_method :original_to_hash, :to_hash
    def to_hash
      original_to_hash.merge(:severity_name => sevname, :adapters => adapters)
    end

    protected
    def default_template_dir
      File.expand_path(File.join(File.dirname(__FILE__), '..', '..', 'templates'))
    end

    # Merge opts from the constructor into here
    def merge opts
      opts.each do |k,v|
        if configurable.key?(k.to_sym) && !v.nil? then
          self.send("#{k}=".to_sym, v)
        end
      end
    end

    ###################################################################################
    # Validators and Formatters
    ###################################################################################
    def format_config_file file
      format_file file
    end
    def valid_config_file? file
      valid_file? file
    end

    def format_logfile file
      format_file file
    end
    def valid_logfile? file
      valid_file? file
    end

    def valid_recipient? recip
      !recip.nil?
    end

    def valid_selector? sel
      !sel.nil? && sel.is_a?(Hash) && !sel.empty?
    end

    def format_severity sev
      sev.to_i unless sev.nil?
    end
    def valid_severity? sev
      !sev.nil? && sev.is_a?(Fixnum) && sev >= -1 && sev <= 3
    end

    def format_template file
      format_file file
    end
    def valid_template? file
      valid_file? file
    end

    def format_template_dir d
      File.expand_path(d.to_s) unless d.nil?
    end
    def valid_template_dir? d
      !d.nil? && File.exists?(d)
    end

    def valid_test? t
      !t.nil? && (t.is_a?(TrueClass) || t.is_a?(FalseClass))
    end

    def format_timeout t
      t.to_i unless t.nil?
    end
    def valid_timeout? t
      !t.nil? && t.is_a?(Fixnum) && t > 0
    end

    def format_type t
      t.to_sym unless t.nil?
    end
    def valid_type? t
      !t.nil? && t.is_a?(Symbol)
    end

    private
    def format_file file
      File.expand_path(file) unless file.nil?
    end
    def valid_file? file
      !file.nil? && File.exists?(file) && File.readable?(file)
    end
  end

end
