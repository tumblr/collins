require 'collins_client'
require 'highline'
require 'yaml'

module CollinsShell
  module ThorHelper

    include Collins::Util

    COLLINS_OPTIONS = {
      :config => {:type => :string, :desc => 'YAML configuration file'},
      :debug => {:type => :boolean, :default => false, :desc => 'Debug output'},
      :host => {:type => :string, :desc => 'Collins host (e.g. http://host:port)'},
      :password => {:type => :string, :desc => 'Collins password'},
      :quiet => {:type => :boolean, :default => false, :desc => 'Be quiet when appropriate'},
      :timeout => {:type => :numeric, :default => 30, :desc => 'Collins client timeout'},
      :username => {:type => :string, :desc => 'Collins username'}
    }

    PAGE_OPTIONS = {
      :page => {:type => :numeric, :default => 0, :desc => 'Page of results set.'},
      :size => {:type => :numeric, :default => 20, :desc => 'Number of results to return.'},
      :sort => {:type => :string, :default => 'DESC', :desc => 'Sort direction. ASC or DESC'}
    }

    def self.included(base)
      base.extend(ThorHelper)
    end
    
    class << self
      attr_accessor :password
    end

    def use_collins_options
      COLLINS_OPTIONS.each do |name, options|
        method_option name, options
      end
    end
    def use_page_options default_size = 20
      PAGE_OPTIONS.each do |name, options|
        options.update(:default => default_size) if name == :size
        method_option name, options
      end
    end
    def use_tag_option required = false
      method_option :tag, :type => :string, :required => required, :desc => 'Tag for asset'
    end
    def use_selector_option required = false
      method_option :selector, :type => :hash, :required => required, :desc => 'Selector to query collins. Takes the form of --selector=key1:val1 key2:val2 etc'
      method_option :remote, :type => :boolean, :default => false, :desc => 'Search all collins instances, including remote ones'
    end

    def selector_or_tag
      if options.selector? then
        options.selector
      elsif options.tag? then
        {:tag => options.tag}
      else
        say_error "Either tag or selector must be specified", :exit => true
      end
    end

    def require_yes message, color = nil, should_exit = true
      def appropriate_answer?(a); na = a.to_s.downcase.strip; na == 'yes' || na == 'no'; end
      highline = HighLine.new
      colored_message = set_color(message, color)
      answer = nil
      while !appropriate_answer?(answer) do
        unless answer.nil? then
          say_status "error", "Please type 'yes' or 'no'.", :red
        end
        answer = ask(colored_message)
      end
      if answer.downcase.strip !~ /^yes$/ then
        if should_exit then
          exit(0)
        else
          false
        end
      else
        true
      end
    end

    def batch_selector_operation options = {}, &block
      confirmation_message = options[:confirmation_message]
      success_message = options[:success_message]
      error_message = options[:error_message]
      operation = options[:operation]
      require_non_empty(confirmation_message, "confirmation_message option not set")
      require_non_empty(success_message, "success_message not set")
      require_non_empty(error_message, "error_message not set")
      require_non_empty(operation, "operation not set")
      selector = get_selector selector_or_tag, [], nil, options[:remote]
      call_collins get_collins_client, operation do |client|
        assets = client.find selector
        if assets.length > 1 then
          require_yes confirmation_message.call(assets), :red
        end
        assets.each do |asset|
          if block.call(client, asset) then
            say_success success_message.call(asset)
          else
            say_error error_message.call(asset)
          end
        end
      end
    end

    def say_error message, options = {}
      if options[:exception] then
        say_status("error", "#{message} - #{options[:exception]}", :red)
        if options[:debug] && options[:debug] then
          pp options[:exception].backtrace
        end
      else
        say_status("error", message, :red)
      end
      if options[:exit].is_a?(TrueClass) then
        exit(1)
      elsif not options[:exit].nil? then
        exit(options[:exit])
      end
    end

    def say_success message
      say_status("success", message, :green)
    end

    def get_collins_client opts = {}
      config = get_collins_config.merge(opts).merge(:strict => true)
      config = ensure_password opts
      require_valid_collins_config config
      config[:logger] = get_logger :trace => options.debug, :progname => 'collins-shell'
      Collins::Client.new config
    end

    def ensure_password opts = {}
      me = CollinsShell::ThorHelper
      if me.password.nil? then
        me.password = get_password(get_collins_config.merge(opts).merge(:strict => true))
      end
      me.password
    end

    def get_password config
      if 
        config[:password] and not
        config[:password].empty? and
        config[:password] != "password" then
        return config
      end
      highline = HighLine.new
      password = highline.ask("Enter your password:  ") { |q| q.echo = "x" }
      config.update(:password => password)
    end

    # --username --password --host is highest priority
    # --config= second highest
    # ~/.collins.yaml is lowest
    def get_collins_config
      def try_config_merge filename, config
        file_config = collins_config_from_file filename
        if file_config[:collins] then
          file_config = file_config[:collins]
        end
        config.update(:host => file_config[:host]) unless options.host?
        config.update(:username => file_config[:username]) unless options.username?
        config.update(:password => file_config[:password]) unless options.password?
        if options.timeout == 30 and file_config[:timeout] then
          config.update(:timeout => file_config[:timeout].to_i)
        end
      end
      config = Hash[
        :host => options.host,
        :username => options.username,
        :password => options.password,
        :timeout => options.timeout
      ]
      if ENV['COLLINS'] then
        try_config_merge ENV['COLLINS'], config
      end
      if options.config? then
        try_config_merge options.config, config
      end
      if File.exists?(File.expand_path("~/.collins.yaml")) then
        user_config = collins_config_from_file "~/.collins.yaml"
        if user_config[:collins] then
          user_config = user_config[:collins]
        end
        config.update(:host => user_config[:host]) if config[:host].nil?
        config.update(:username => user_config[:username]) if config[:username].nil?
        config.update(:password => user_config[:password]) if config[:password].nil?
        if config[:timeout] == 30 and user_config[:timeout] then
          config.update(:timeout => user_config[:timeout].to_i)
        end
      end
      config
    end

    def collins_config_from_file file
      symbolize_hash(YAML::load(File.open(File.expand_path(file))))
    end

    def require_valid_collins_config config
      begin
        require_non_empty(config[:host], "collins.host is required")
        require_non_empty(config[:username], "collins.username is required")
        require_non_empty(config[:password], "collins.password is required")
      rescue Exception => e
        raise CollinsShell::ConfigurationError.new(e.message)
      end
    end

  end
end
