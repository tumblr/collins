require 'ostruct'

module Collins; module Api

  # @api collins
  module Tag

    include Collins::Util

    # Get all collins tags
    #
    # Sample output
    # @example
    #  [
    #    {:name => "", :label => "", :description => ""},
    #  ]
    #  
    # @raise [UnexpectedResponseError] if strict is true and the response is not a 200
    # @return [Array<OpenStruct>] Array of tags containing name, label and description keys
    def get_all_tags
      http_get("/api/tags") do |response|
        parse_response response, :expects => 200, :default => [], :raise => strict? do |json|
          json["data"]["tags"].map{|t| OpenStruct.new(symbolize_hash(t))}
        end
      end
    end

    # Get all values associated with the specified tag
    # 
    # @note You will get a 404 if the tag does not exist. Depending on strict mode, this will result in an empty array or an exception
    # @param [String] tag The tag you would like values for
    # @param [Boolean] strict Here for backwards API compatibility
    # @raise [UnexpectedResponseError] if strict is true and the response is not a 200
    # @return [Array<String>] values associated with this tag
    def get_tag_values tag, strict = false
      http_get("/api/tag/#{tag}") do |response|
        parse_response response, :expects => 200, :default => [], :raise => strict?(strict) do |json|
          json["data"]["values"]
        end
      end
    end

  end # module Tag

end; end

