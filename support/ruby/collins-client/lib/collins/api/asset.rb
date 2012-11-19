require 'uri'

module Collins; module Api

  module Asset

    def create! asset_or_tag, options = {}
      asset = get_asset_or_tag asset_or_tag
      parameters = {
        :generate_ipmi => get_option(:generate_ipmi, options, false),
        :status => get_option(:status, options, asset.status),
        :type => get_option(:type, options, asset.type)
      }
      parameters = select_non_empty_parameters parameters
      logger.debug("Creating asset #{asset.tag} with parameters #{parameters.inspect}")
      http_put("/api/asset/#{asset.tag}", parameters) do |response|
        parse_response response, :expects => 201, :as => :asset
      end
    end

    def delete! asset_or_tag, options = {}
      asset = get_asset_or_tag asset_or_tag
      parameters = {
        :reason => get_option(:reason, options, nil)
      }
      parameters = select_non_empty_parameters parameters
      logger.debug("Deleting asset #{asset.tag} with parameters #{parameters.inspect}")
      http_delete("/api/asset/#{asset.tag}", parameters, asset.location) do |response|
        parse_response response, :expects => 200, :as => :status, :raise => strict?, :default => false
      end
    end

    def exists? asset_or_tag, status = nil
      begin
        asset = get(asset_or_tag)
        if asset && status && asset.status.downcase == status.downcase then
          true
        elsif asset && status.nil? then
          true
        else
          false
        end
      rescue Collins::RequestError => e
        if e.code.to_i == 404 then
          false
        else
          # if strict? is true, should still return a boolean for exists?
          logger.info("Exception getting asset: #{e.class} #{e}")
          false
        end
      rescue Exception => e
        if e.class.to_s == "WebMock::NetConnectNotAllowedError" then
          raise e
        end
        # if strict? is true, should still return a boolean for exists?
        logger.info("Exception getting asset: #{e.class} - #{e}")
        false
      end
    end

    def get asset_or_tag, options = {}
      asset = get_asset_or_tag asset_or_tag
      parameters = {
        :location => get_option(:location, options, nil)
      }
      parameters = select_non_empty_parameters parameters
      logger.debug("Getting asset #{asset.tag} with params #{parameters.inspect}")
      http_get("/api/asset/#{asset.tag}", parameters, asset.location) do |response|
        parse_response response, :as => :asset, :expects => 200, :raise => strict?, :default => false
      end
    end

    # Find assets matching the specified criteria
    #
    # In general the options hash corresponds to asset key/value pairs. Reserved keys in the selector
    # are treated with care. These keys are in {Collins::Asset::Find::DATE\_PARAMS} and in
    # {Collins::Asset::Find::GENERAL\_PARAMS}. All other keys are assumed to be asset attributes.
    # @param [Hash] options Query options
    # @return [Array<Collins::Asset>] An array of assets matching the query
    # @raise [UnexpectedResponseError] If the HTTP response code is not a 200
    def find options = {}
      solrquery = options.delete(:query)
      if solrquery != nil then
        return search solrquery, (options[:size] || 50), (options[:sort] || "ASC"), "tag", options
      end
      use_api_version "1.1"
      query = asset_hash_to_find_query options
      params = query.to_a.map do |param|
        key, val = param
        if val.is_a?(Array) then
          val.map{|v| "#{key}=#{asset_escape_attribute(v)}"}.join("&")
        else
          "#{key}=#{asset_escape_attribute(val)}"
        end
      end.reject{|s| s.empty?}
      logger.debug("Finding assets using params #{params.inspect}")
      http_get("/api/assets", params) do |response|
        parse_response response, :expects => 200, :as => :paginated do |json|
          json.map { |j| Collins::Asset.from_json(j) }
        end
      end
    end

    def search query, size = 50, sort = "ASC", sort_field = "tag", options = {}
      use_api_version "1.2"
      if query.start_with? "\"" and query.end_with? "\"" then
        query = query[1..-2]
      end
      params = {
        :query => query,
        :size => size,
        :sort => sort,
        :sort_field => sort_field
      }
      query = asset_hash_to_find_query options.merge(params)
      params = query.to_a.map do |(key,val)|
        if val.is_a?(Array) then
          val.map{|v| "#{key}=#{asset_escape_attribute(v)}"}.join("&")
        elsif val.nil? then
          ""
        else
          "#{key}=#{asset_escape_attribute(val)}"
        end
      end.reject{|s| s.empty?}
      logger.debug("perform asset search using query #{query} - #{params.inspect}")
      http_get("/api/assets",params) do |response|
        parse_response response, :expects => 200, :as => :paginated do |json|
          json.map { |j| Collins::Asset.from_json(j) }
        end
      end
    end

    def find_similar asset_or_tag, size = 50, sort = "ASC", sort_type = "distance", only_unallocated = true
      asset = get_asset_or_tag asset_or_tag
      params = {
        :size => size,
        :sort => sort,
        :sortType => sort_type,
        :onlyUnallocated => only_unallocated
      }
      logger.debug("Finding similar assets for #{asset.tag}")
      http_get("/api/asset/#{asset.tag}/similar", params) do |response|
        parse_response response, :expects => 200, :as => :paginated do |json|
          json.map { |j| Collins::Asset.from_json(j) }
        end
      end
    end

    private
    def asset_escape_attribute value
      URI.escape(value.to_s, Regexp.new("[^#{URI::PATTERN::UNRESERVED}]"))
    end

    def asset_hash_to_find_query opts = {}
      options = opts.clone
      hash = {:attribute => []}
      okeys = options.keys
      Collins::Asset::Find::DATE_PARAMS.each do |query_key|
        okeys.each do |user_key|
          if query_key.to_s.downcase == user_key.to_s.downcase then
            hash[query_key.to_sym] = Collins::Asset.format_date_string(options.delete(user_key))
          end
        end
      end
      Collins::Asset::Find::GENERAL_PARAMS.each do |query_key|
        okeys.each do |user_key|
          if query_key.to_s.downcase == user_key.to_s.downcase then
            hash[query_key.to_sym] = options.delete(user_key)
          end
        end
      end
      options.each do |k,v|
        hash[:attribute] << "#{k};#{regex_to_string(v)}"
      end
      hash
    end

    def regex_to_string value
      if is_regex? value then
        rewrite_regex value
      else
        value
      end
    end
    def is_regex? value
      value.is_a?(Regexp)
    end
    def rewrite_regex value
      value.inspect[1..-2] # turn /lkasd/ into lkasd
    end

  end

end; end
