require 'collins/address'

module Collins; module Api

  module IpAddress
    def ipaddress_allocate! asset_or_tag, address_pool, count = 1
      asset = get_asset_or_tag asset_or_tag
      logger.debug("Allocating #{count} addresses for #{asset.tag} in pool #{address_pool}")
      parameters = {
        :count => count,
        :pool => address_pool
      }
      http_put("/api/asset/#{asset.tag}/address", parameters, asset.location) do |response|
        parse_response response, :expects => 201, :default => [] do |json|
          Collins::Address.from_json(json["data"]["ADDRESSES"])
        end
      end
    end

    def ipaddress_update! asset_or_tag, old_address = nil, options = {}
      asset = get_asset_or_tag asset_or_tag
      logger.debug("Updating IP address for #{asset.tag}")
      parameters = {
        :old_address => old_address,
        :address => get_option(:address, options, nil),
        :gateway => get_option(:gateway, options, nil),
        :netmask => get_option(:netmask, options, nil),
        :pool => get_option(:pool, options, nil)
      }
      parameters = select_non_empty_parameters parameters
      http_post("/api/asset/#{asset.tag}/address", parameters, asset.location) do |response|
        parse_response response, :expects => [200,201], :default => false, :raise => strict?
      end
    end

    def ipaddress_delete! asset_or_tag, pool = nil
      asset = get_asset_or_tag asset_or_tag
      logger.debug("Deleting addresses for asset #{asset.tag} in pool #{pool}")
      parameters = {
        :pool => pool
      }
      parameters = select_non_empty_parameters parameters
      http_delete("/api/asset/#{asset.tag}/addresses", parameters, asset.location) do |response|
        parse_response response, :expects => 200, :default => false, :raise => strict? do |json|
          json["data"]["DELETED"].to_s.to_i
        end
      end
    end

    def ipaddress_pools show_all = true
      logger.debug("Finding IP address pools")
      http_get("/api/address/pools", {:all => show_all}) do |response|
        parse_response response, :expects => 200, :default => [], :raise => strict? do |json|
          json["data"]["POOLS"]
        end
      end
    end

    def addresses_for_asset asset_or_tag
      asset = get_asset_or_tag asset_or_tag
      logger.debug("Getting IP addresses for asset #{asset.tag}")
      http_get("/api/asset/#{asset.tag}/addresses", {}, asset.location) do |response|
        parse_response response, :expects => 200, :default => [], :raise => strict? do |json|
          Collins::Address.from_json(json["data"]["ADDRESSES"])
        end
      end
    end

    def asset_at_address address
      logger.debug("Finding asset at address #{address}")
      http_get("/api/asset/with/address/#{address}") do |response|
        parse_response response, :expects => 200, :default => nil, :raise => strict?, :as => :bare_asset
      end
    end

    def assets_in_pool pool
      logger.debug("Finding assets in pool #{pool}")
      http_get("/api/assets/with/addresses/in/#{pool}") do |response|
        parse_response response, :expects => 200, :default => [], :raise => strict? do |json|
          json["data"]["ASSETS"].map{|j| Collins::Asset.from_json(j, true)}
        end
      end
    end

  end # module IpAddress

end; end
