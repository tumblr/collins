require 'ostruct'

module Collins; module Api

  module Logging

    include Collins::Util

    # @see http://tumblr.github.com/platform/collinsTutorial/out/logapi.html
    module Severity
      extend self

      EMERGENCY     = "EMERGENCY"
      ALERT         = "ALERT"
      CRITICAL      = "CRITICAL"
      ERROR         = "ERROR"
      WARNING       = "WARNING"
      NOTICE        = "NOTICE"
      INFORMATIONAL = "INFORMATIONAL"
      DEBUG         = "DEBUG"
      NOTE          = "NOTE"

      # Given a severity level, give back the severity, or nil if not valid
      # @param [String,Symbol] level Severity level
      # @return [String] Severity level as string
      def value_of level
        level_s = normalize level
        if valid? level_s then
          level_s.to_s
        else
          nil
        end
      end

      # Convert a level into one appropriate for validating
      # @param [Symbol,String] level Severity level
      # @return [Symbol] normalized (not neccesarily valid) severity level
      def normalize level
        level.to_s.upcase.to_sym
      end

      # Check is a level is valid or not
      # @param [Symbol,String] level Severity level
      # @return [Boolean] indicate whether valid or not
      def valid? level
        level_s = normalize level
        Collins::Api::Logging::Severity.constants.include?(level_s)
      end

      #@return [Array<String>] severity levels
      def to_a
        s = Collins::Api::Logging::Severity
        s.constants.map{|c| s.const_get(c)}
      end
    end # module Severity

    # Log a message against an asset using the specified level
    # @param [String,Collins::Asset] asset_or_tag
    # @param [String] message
    # @param [Severity] level severity level to use
    # @return [Boolean] true if logged successfully
    # @raise [Collins::ExpectationFailed] the specified level was invalid
    # @raise [Collins::RequestError,Collins::UnexpectedResponseError] if the asset or message invalid
    def log! asset_or_tag, message, level = nil
      asset = get_asset_or_tag asset_or_tag
      parameters = {
        :message => message,
        :type => log_level_from_string(level)
      }
      parameters = select_non_empty_parameters parameters
      logger.debug("Logging to #{asset.tag} with parameters #{parameters.inspect}")
      http_put("/api/asset/#{asset.tag}/log", parameters, asset.location) do |response|
        parse_response response, :as => :status, :expects => 201
      end
    end

    # Fetch logs for an asset according to the options specified
    # @example
    #   :filter => "EMERGENCY;ALERT" # Only retrieve emergency and alert messages
    #   :filter => "!DEBUG;!NOTE"    # Only retrieve non-debug/non-notes
    # @param [String,Collins::Asset] asset_or_tag
    # @param [Hash] options query options
    # @option options [Fixnum] :page (0) Page of results
    # @option options [Fixnum] :size (25) Number of results to retrieve
    # @option options [String] :sort (DESC) Sort ordering for results
    # @option options [String] :filter Semicolon separated list of severity levels to include or exclude
    # @option options [String] :all_tag If specified, an asset tag is this value, proxy to the all_logs method
    # @note To exclude a level via a filter it must be prepended with a `!`
    # @return [Array<OpenStruct>] Array of log objects
    # @raise [Collins::UnexpectedResponseError] on a non-200 response
    def logs asset_or_tag, options = {}
      asset = get_asset_or_tag asset_or_tag
      all_tag = options.delete(:all_tag)
      if all_tag && all_tag.to_s.downcase == asset.tag.to_s.downcase then
        return all_logs options
      end
      parameters = get_page_options(options).merge(
        :filter => get_option(:filter, options, nil)
      )
      parameters = select_non_empty_parameters parameters
      logger.debug("Fetching logs for #{asset.tag} with parameters #{parameters.inspect}")
      http_get("/api/asset/#{asset.tag}/logs", parameters, asset.location) do |response|
        parse_response response, :as => :paginated, :default => [], :raise => strict?, :expects => 200 do |json|
          json.map{|j| OpenStruct.new(symbolize_hash(j))}
        end
      end
    end

    # new solr interface
    def search_logs options = {}
      parameters = get_page_options(options).merge(
        :query => get_option(:query, options, nil),
        :sortField => get_option(:sortField, options, 'ID')
      )
      parameters = select_non_empty_parameters parameters
      logger.debug("Fetching logs for all assets with parameters #{parameters.inspect}")
      http_get("/api/assets/logs/search", parameters) do |response|
        parse_response response, :as => :paginated, :default => [], :raise => strict?, :expects => 200 do |json|
          json.map{|j| OpenStruct.new(symbolize_hash(j))}
        end
      end
    end

    # Same as logs but for all assets
    # @see #logs
    def all_logs options = {}
      parameters = get_page_options(options).merge(
        :filter => get_option(:filter, options, nil)
      )
      parameters = select_non_empty_parameters parameters
      logger.debug("Fetching logs for all assets with parameters #{parameters.inspect}")
      http_get("/api/assets/logs", parameters) do |response|
        parse_response response, :as => :paginated, :default => [], :raise => strict?, :expects => 200 do |json|
          json.map{|j| OpenStruct.new(symbolize_hash(j))}
        end
      end
    end

    private
    def log_level_from_string level
      return nil if (level.nil? || level.empty?)
      s = Collins::Api::Logging::Severity
      if s.valid? level then
        s.value_of level
      else
        raise Collins::ExpectationFailedError.new("#{level} is not a valid log level")
      end
    end

  end # module Log

end; end
