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
    def set_status! asset_or_tag, status, reason = nil
      asset = get_asset_or_tag asset_or_tag
      parameters = {
        :status => status,
        :reason => reason
      }
      parameters = select_non_empty_parameters parameters
      logger.debug("Setting status to #{status} on #{asset.tag}")
      http_post("/api/asset/#{asset.tag}", parameters, asset.location) do |response|
        parse_response response, :expects => 200, :as => :status, :raise => strict?, :default => false
      end
    end
  end # module Attributes

end; end
