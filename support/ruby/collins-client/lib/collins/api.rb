require 'collins/api/asset'
require 'collins/api/attributes'
require 'collins/api/ip_address'
require 'collins/api/logging'
require 'collins/api/management'
require 'collins/api/tag'
require 'collins/api/util'

module Collins
  module Api

    # Provides get_asset_or_tag, require stuff, and symbolize
    include Collins::Util

    # @abstract
    # @return [Hash<String,String>] hash with header keys/values
    def headers
      raise NotImplementedError.new("Classes including the Api module must provide a headers hash")
    end

    # @abstract
    # @return [String] the collins host
    def host
      raise NotImplementedError.new("Classes including the Api module must provide a host")
    end

    # Only used for multi-collins systems
    # @abstract
    # @return [Hash<Symbol,OpenStruct>] hash with keys as locations, values as collins credentials.
    def locations
      raise NotImplementedError.new("Classes including the Api module must provide a locations hash")
    end

    # @abstract
    # @return [Logger] a logger instance
    def logger
      raise NotImplementedError.new("Classes including the Api module must provide a logger")
    end

    # @abstract
    # @return [String] a password for authentication
    def password
      raise NotImplementedError.new("Classes including the Api module must provide a password")
    end

    # How to deal with unexpected API responses
    #
    # When true, API methods will throw an exception if an unexpected response is encountered.
    # When false, API methods will usually normalize responses to an appropriate value indicating
    # failure.
    #
    # @param [Boolean] default
    # @abstract
    # @return [Boolean] strict or not
    def strict? default = false
      raise NotImplementedError.new("Classes including the Api module must provide a strict? method")
    end

    # @abstract
    # @return [Fixnum] a timeout in seconds
    def timeout_i
      raise NotImplementedError.new("Classes including the Api module must provide a timeout")
    end

    # @abstract
    # @return [String] a username for authentication
    def username
      raise NotImplementedError.new("Classes including the Api module must provide a username")
    end

    # Clear out all headers
    # @return [nil]
    def clear_headers
      headers.clear # Yes, this returns an empty hash not nil
    end

    # Set a key/value in the headers hash
    #
    # @param [Symbol,String] key
    # @param [String] value
    # @return [nil]
    def set_header key, value
      headers.update(key => value)
    end

    # Provides a safe wrapper for our monkeypatched logger
    #
    # If the provided logger responds to a trace method, use that method. Otherwise fallback to
    # using the debug method.
    def trace(progname = nil, &block)
      if logger.respond_to?(:trace) then
        logger.trace(progname, &block)
      else
        logger.debug(progname, &block)
      end
    end

    def use_api_version version
      set_header "Accept", "application/json,#{version_string(version)}"
    end

    include Collins::Api::Asset
    include Collins::Api::Attributes
    include Collins::Api::IpAddress
    include Collins::Api::Logging
    include Collins::Api::Management
    include Collins::Api::Tag
    include Collins::Api::Util

    protected
    def version_string version
      "application/com.tumblr.collins;version=#{version}"
    end
  end
end
