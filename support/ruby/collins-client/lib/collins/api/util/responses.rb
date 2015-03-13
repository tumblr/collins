require 'collins/api/util/errors'

module Collins; module Api; module Util

  module Responses
    include Collins::Api::Util::Errors

    protected
    def parse_response response, options
      do_raise = options[:raise] != false
      if options.include?(:expects) && ![options[:expects]].flatten.include?(response.code) then
        handle_error(response) if do_raise
        if options.include?(:default) then
          return options[:default]
        else
          raise UnexpectedResponseError.new("Expected code #{options[:expects]}, got #{response.code}")
        end
      end
      handle_error(response) if do_raise
      json = response.parsed_response
      if options.include?(:as) then
        case options[:as]
        when :asset
          json = Collins::Asset.from_json(json)
        when :bare_asset
          json = Collins::Asset.from_json(json, true)
        when :data
          json = json["data"]["Data"]
        when :status
          json = json["data"]["SUCCESS"]
        when :message
          json = json["data"]["MESSAGE"]
        end
      end
      if block_given? then
        yield(json)
      else
        json
      end
    end

  end # Responses module

end; end; end
