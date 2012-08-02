require 'collins/logging'

module Collins

  # General purpose util methods
  #
  # Methods in {Collins::Util} can be accessed by....
  # 
  #  * `Collins::Util.method_name`
  #  * `ClassIncludingCollinsUtil.method_name`
  #  * `instance_of_class_including_collins_util.method_name`
  #
  # Other than {Util#get\_asset\_or\_tag} most of these methods are not collins specific
  module Util

    include Collins::Util::Logging

    def self.included base
      base.extend(Collins::Util)
    end

    # Create a deep copy of a hash
    #
    # This is useful for copying a hash that will be mutated
    # @note All keys and values must be serializable, Proc for instance will fail
    # @param [Hash] hash the hash to copy
    # @return [Hash]
    def deep_copy_hash hash
      require_that(hash.is_a?(Hash), "deep_copy_hash requires a hash be specified, got #{hash.class}")
      Marshal.load Marshal.dump(hash)
    end

    # Require that a value not be empty
    #
    # If the value is a string, ensure that once stripped it's not empty. If the value responds to
    # `:empty?`, ensure that it's not. Otherwise ensure the value isn't nil.
    #
    # @param [Object] value the value to check
    # @param [String] message the exception message to use if the value is empty
    # @param [Boolean,Object] return_value If true, returns value. If not false, returns the object
    # @raise [ExpectationFailedError] if the value is empty
    # @return [NilClass,Object] NilClass, or respecting return_value
    def require_non_empty value, message, return_value = false
      guard_value = if return_value == true then
                      value
                    elsif return_value != false then
                      return_value
                    else
                      false
                    end
      if value.is_a?(String) then
        require_that(!value.strip.empty?, message, guard_value)
      elsif value.respond_to?(:empty?) then
        require_that(!value.empty?, message, guard_value)
      else
        require_that(!value.nil?, message, guard_value)
      end
    end

    # Require that a guard condition passes
    #
    # Simply checks that the guard is truthy, and throws an error otherwise
    # @see #require_non_empty
    def require_that guard, message, return_guard = false
      if not guard then
        raise ExpectationFailedError.new(message)
      end
      if return_guard == true then
        guard
      elsif return_guard != false then
        return_guard
      end
    end

    # Resolve an asset from a string tag or collins asset
    # @note This is perhaps the only collins specific method in Util
    # @param [Collins::Asset,String,Symbol] asset_or_tag
    # @return [Collins::Asset] a collins asset
    # @raise [ExpectationFailedError] if asset\_or\_tag isn't valid
    def get_asset_or_tag asset_or_tag
      asset =
        case asset_or_tag
          when Collins::Asset then asset_or_tag
          when String then Collins::Asset.new(asset_or_tag)
          when Symbol then Collins::Asset.new(asset_or_tag.to_s)
        else
          error_message = "Expected Collins::Asset, String or Symbol. Got #{asset_or_tag.class}"
          raise ExpectationFailedError.new(error_message)
        end
      if asset.nil? || asset.tag.nil? then
        raise ExpectationFailedError.new("Empty asset tag, but a tag is required")
      end
      asset
    end

    # Given a hash, rewrite keys to symbols
    #
    # @param [Hash] hash the hash to symbolize
    # @param [Hash] options specify how to process the hash
    # @option options [Boolean] :rewrite_regex if the value is a regex and this is true, convert it to a string
    # @option options [Boolean] :downcase if true, downcase the keys as well
    # @raise [ExpectationFailedError] if hash is not a hash
    def symbolize_hash hash, options = {}
      return {} if (hash.nil? or hash.empty?)
      (raise ExpectationFailedError.new("symbolize_hash called without a hash")) unless hash.is_a?(Hash)
      hash.inject({}) do |result, (k,v)|
        key = options[:downcase] ? k.to_s.downcase.to_sym : k.to_s.to_sym
        if v.is_a?(Hash) then
          result[key] = symbolize_hash(v)
        elsif v.is_a?(Regexp) && options[:rewrite_regex] then
          result[key] = v.inspect[1..-2]
        else
          result[key] = v
        end
        result
      end
    end

    # Given a hash, convert all keys to strings
    # @see #symbolize_hash
    def stringify_hash hash, options = {}
      (raise ExpectationFailedError.new("stringify_hash called without a hash")) unless hash.is_a?(Hash)
      hash.inject({}) do |result, (k,v)|
        key = options[:downcase] ? k.to_s.downcase : k.to_s
        if v.is_a?(Hash) then
          result[key] = stringify_hash(v)
        elsif v.is_a?(Regexp) && options[:rewrite_regex] then
          result[key] = v.inspect[1..-2]
        else
          result[key] = v
        end
        result
      end
    end

    # This provides access to these methods via a Collins::Util.method_name call
    [:deep_copy_hash, :require_non_empty, :get_asset_or_tag, :require_that,
     :symbolize_hash, :stringify_hash
    ].each do |method|
      module_function method
      public method # without this, module_function makes the method private
    end
  end # Util module

end # Collins module
