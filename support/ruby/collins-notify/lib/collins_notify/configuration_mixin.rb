module CollinsNotify; module ConfigurationMixin

  def config
    raise NotImplementedError.new "ConfigurationMixin#config must be implemented"
  end

  def valid?
    raise NotImplementedError.new "ConfigurationMixin#valid? must be implemented"
  end

  def fatal?; severity <= Logger::FATAL; end
  def error?; severity <= Logger::ERROR; end
  def warn?; severity <= Logger::WARN; end
  def info?; severity <= Logger::INFO; end
  def debug?; severity <= Logger::DEBUG; end
  def trace?; severity <= Logger::TRACE; end
  def sevname
    if trace? then
      "TRACE"
    else
      Logger::SEV_LABEL[severity]
    end
  end

  # Handles magic getters/setters
  def method_missing m, *args, &block
    kname = m.to_s.gsub(/=$/, '') # key name, remove = in case it's assignment
    is_configurable = configurable.key?(kname.to_sym) 
    is_assignment = is_configurable && m.to_s[-1].eql?('=') && args.size > 0

    # handles gets, these are the simplest
    if is_configurable && !is_assignment then
      get_cfg_var "@#{kname}".to_sym
    elsif is_assignment then # handle set case
      formatter_name = "format_#{kname}".to_sym
      if args.size == 1 then
        value = args.first
      else
        value = args
      end

      # Format value if a formatter exists
      if respond_to?(formatter_name) then
        value = send(formatter_name, value)
      end

      validator_name = "valid_#{kname}?".to_sym
      unless respond_to?(validator_name) then
        raise NotImplementedError.new "ConfigurationMixin##{validator_name} must be implemented"
      end
      if send(validator_name, value) then
        set_cfg_var "@#{kname}".to_sym, value
      else
        raise CollinsNotify::ConfigurationError.new "#{kname} #{value.inspect} is not valid"
      end
    else
      super
    end
  end

  def to_hash
    configurable.inject({}) do |ret, (k,v)|
      ret.update(k => get_cfg_var("@#{k.to_s}".to_sym))
    end
  end

  protected
  def configurable
    {
      :config_file      => nil,
      :logfile          => nil,
      :recipient        => nil,
      :selector         => {}, # optional collins asset to notify about
      :severity         => Logger::INFO,
      :template         => nil,
      :template_dir     => nil, # legacy needs
      :test             => false,
      :timeout          => 10,
      :type             => nil,
    }
  end

  def get_cfg_var name
    config.instance_variable_get name
  end

  def set_cfg_var name, value
    config.instance_variable_set name, value
  end

end; end
