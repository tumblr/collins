require 'collins/api'
require 'collins/asset_client'
require 'httparty'

module Collins

  # Primary interface for interacting with collins
  #
  # @example
  #   client = Collins::Client.new :host => '...', :username => '...', :password => '...'
  #   client.get 'asset_tag'
  class Client

    # @see Collins::Api#host
    attr_reader :host
    # @see Collins::Api#locations
    attr_reader :locations
    # @see Collins::Api#logger
    attr_reader :logger
    # @see Collins::Api#timeout_i
    attr_reader :timeout_i
    # @see Collins::Api#password
    attr_reader :password
    # @return [Boolean] strict mode throws exceptions when unexpected responses occur
    attr_reader :strict
    # @see Collins::Api#username
    attr_reader :username

    include HTTParty
    include Collins::Api
    include Collins::Util

    # Create a collins client instance
    # @param [Hash] options host, username and password are required
    # @option options [String] :host a scheme, hostname and port (e.g. https://hostname)
    # @option options [Logger] :logger a logger to use, one is created if none is specified
    # @option options [Fixnum] :timeout (10) timeout in seconds to wait for a response
    # @option options [String] :username username for authentication
    # @option options [String] :password password for authentication
    # @option options [String] :managed_process see {#manage_process}
    # @option options [Boolean] :strict (false) see {#strict}
    def initialize options = {}
      config = symbolize_hash options
      @locations = {}
      @host = fix_hostname(config.fetch(:host, ""))
      @logger = get_logger config.merge(:progname => 'Collins_Client')
      @timeout_i = config.fetch(:timeout, 10).to_i
      @username = config.fetch(:username, "")
      @password = config.fetch(:password, "")
      @strict = config.fetch(:strict, false)
      @managed_process = config.fetch(:managed_process, nil)
      require_non_empty(@host, "Collins::Client host must be specified")
      require_non_empty(@username, "Collins::Client username must be specified")
      require_non_empty(@password, "Collins::Client password must be specified")
    end

    # Interact with a collins managed process
    # @param [String] name Name of process
    # @raise [CollinsError] if no managed process is specified/found
    # @return [Collins::ManagedState::Mixin] see mixin for more information
    def manage_process name = nil
      name = @managed_process if name.nil?
      if name then
        begin
          Collins.const_get(name).new(self).run
        rescue Exception => e
          raise CollinsError.new(e.message)
        end
      else
        raise CollinsError.new("No managed process specified")
      end
    end

    # @return [String] Collins::Client(host = hostname)
    def to_s
      "Collins::Client(host = #{@host})"
    end

    # @see Collins::Api#strict?
    def strict? default = false
      @strict || default
    end

    # Use the specified asset for subsequent method calls
    # @param [Collins::Asset,String] asset The asset to use for operations
    # @return [Collins::AssetClient] Provides most of the same methods as {Collins::Client} but with no need to specfiy the asset for those methods
    def with_asset asset
      Collins::AssetClient.new(asset, self, @logger)
    end

    protected
    def fix_hostname hostname
      hostname.is_a?(String) ? hostname.gsub(/\/+$/, '') : hostname
    end

  end
end
