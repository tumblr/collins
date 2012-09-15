module Collins; module Api

  module Attributes
    def delete_attribute! asset_or_tag, attribute, group_id = nil
      asset = get_asset_or_tag asset_or_tag
      parameters = {
        :groupId => group_id
      }
      parameters = select_non_empty_parameters parameters
      logger.debug("Deleting attribute #{attribute} on #{asset.tag} with params #{parameters.inspect}")
      http_delete("/api/asset/#{asset.tag}/attribute/#{attribute}", parameters, asset.location) do |response|
        parse_response response, :expects => 202, :as => :status, :raise => strict?, :default => false
      end
    end
    def set_attribute! asset_or_tag, key, value, group_id = nil
      asset = get_asset_or_tag asset_or_tag
      parameters = {
        :attribute => "#{key};#{value}",
        :groupId => group_id
      }
      parameters = select_non_empty_parameters parameters
      logger.debug("Setting attribute #{key} to #{value} on #{asset.tag}")
      http_post("/api/asset/#{asset.tag}", parameters, asset.location) do |response|
        parse_response response, :expects => 200, :as => :status, :raise => strict?, :default => false
      end
    end

    # Set the status of an asset
    # @overload set_status!(asset_or_tag, status, reason = 'Set via API', state = nil)
    #   Set the status, reason and optionally state of asset
    #   @param [String,Collins::Asset] asset_or_tag The asset or tag
    #   @param [String] status the status of the asset
    #   @param [String] reason the reason for the change
    #   @param [String] state the asset state
    # @overload set_status!(asset_or_tag, hash)
    #   Set the status, reason, and optionally state of asset
    #   @param [String,Collins::Asset] asset_or_tag The asset or tag
    #   @param [Hash] hash the options to set
    #   @option hash [String] :status The asset status
    #   @option hash [String] :reason The reason for the change
    #   @option hash [String] :state The asset state
    # @return Boolean
    def set_status! asset_or_tag, *varargs
      status = state = nil
      reason = 'Set via ruby client'
      asset = get_asset_or_tag asset_or_tag
      if varargs.size == 0 then
        raise ::Collins::ExpectationFailedError.new("set_status! requires a status")
      elsif varargs.size == 1 and varargs[0].is_a?(Hash) then
        hash = symbolize_hash(varargs[0], :downcase => true)
        status = hash[:status]
        reason = hash.fetch(:reason, reason)
        state = hash[:state]
      elsif varargs.size == 1 and (varargs[0].is_a?(String) or varargs[0].is_a?(Symbol)) then
        status = varargs[0].to_s
      elsif varargs.size > 1 then
        status = varargs[0]
        reason = varargs[1]
        state = varargs[2] if varargs.size > 2
      else
        raise ::Collins::ExpectationFailedError.new("set_status! called with invalid parameters")
      end
      parameters = {
        :status => status,
        :reason => reason,
        :state => state
      }
      parameters = select_non_empty_parameters parameters
      logger.debug("Setting status to #{status} on #{asset.tag}")
      http_post("/api/asset/#{asset.tag}/status", parameters, asset.location) do |response|
        parse_response response, :expects => 200, :as => :status, :raise => strict?, :default => false
      end
    end
  end # module Attributes

end; end
