require 'collins/power'
require 'collins/profile'

module Collins; module Api

  module Management

    def provisioning_profiles
      logger.debug("Getting provisioning profiles from collins")
      http_get("/api/provision/profiles") do |response|
        parse_response response, :expects => 200, :as => :data do |json|
          json["PROFILES"].map { |o| Collins::Profile.new(o) }
        end
      end
    end

    def provision asset_or_tag, profile, contact, options = {}
      asset = get_asset_or_tag asset_or_tag
      parameters = {
        :profile => profile,
        :contact => contact,
        :suffix => options[:suffix],
        :primary_role => options[:primary_role],
        :secondary_role => options[:secondary_role],
        :pool => options[:pool],
        :activate => options[:activate]
      }
      parameters = select_non_empty_parameters parameters
      if parameters.empty? then
        raise CollinsError.new("provision requires at least a profile")
      end
      http_post("/api/provision/#{asset.tag}", parameters, asset.location) do |response|
        parse_response response, :expects => 200, :as => :status
      end
    end

    def power_status asset_or_tag
      asset = get_asset_or_tag asset_or_tag
      logger.debug("Checking power status of #{asset.tag}")
      http_get("/api/asset/#{asset.tag}/power", {}, asset.location) do |response|
        parse_response response, :expects => 200, :as => :message, :raise => strict?, :default => "Unknown"
      end
    end

    def power! asset_or_tag, action
      asset = get_asset_or_tag asset_or_tag
      if action.to_s.downcase == "status" then
        return power_status asset_or_tag
      end
      action = Collins::Power.normalize_action action
      parameters = {
        :action => action
      }
      logger.debug("Calling power action on #{asset.tag}, action #{action}")
      http_post("/api/asset/#{asset.tag}/power", parameters, asset.location) do |response|
        parse_response response, :expects => 200, :as => :status
      end
    end

    def ipmi_create asset_or_tag, username, password, address, gateway, netmask
      ipmi_update asset_or_tag, :ipmiuser => username, :ipmipass => password, :address => address,
                                :gateway => gateway, :netmask => netmask
    end

    def ipmi_update asset_or_tag, options = {}
      asset = get_asset_or_tag asset_or_tag
      parameters = {
        :ipmiuser => get_option(:ipmiuser, options, nil),
        :ipmipass => get_option(:ipmipass, options, nil),
        :address => get_option(:address, options, nil),
        :gateway => get_option(:gateway, options, nil),
        :netmask => get_option(:netmask, options, nil)
      }
      parameters = select_non_empty_parameters parameters
      return true if parameters.empty?
      logger.debug("Updating asset #{asset.tag} IPMI info with parameters #{parameters.inspect}")
      http_post("/api/asset/#{asset.tag}/ipmi", parameters, asset.location) do |response|
        parse_response response, :expects => [200,201], :as => :status
      end
    end

  end # module Management

end; end
