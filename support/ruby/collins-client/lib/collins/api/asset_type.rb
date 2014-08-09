require 'collins/asset_type'
module Collins; module Api

  module AssetType
    def asset_type_test; @asset_type_test end
    def asset_type_test= v; @asset_type_test = v end
    module_function :asset_type_test, :asset_type_test=

    def asset_type_create! name, label
      name = validate_name name
      parameters = { :label => label }

      if not ::Collins::Api::AssetType.asset_type_test then
        parameters = select_non_empty_parameters parameters
      end

      logger.debug("Creating asset type with name #{name}")
      http_put("/api/assettype/#{name}", parameters) do |r|
        parse_response r, :expects => 201, :as => :status, :raise => strict?, :default => false
      end
    end

    def asset_type_delete! atype
      name = validate_name case atype.class
      when ::Collins::AssetType
        atype.name
      else
        atype
      end

      logger.debug("Deleting asset type with name #{name}")
      http_delete("/api/assettype/#{name}") do |r|
        parse_response r, :expects => 202, :as => :data, :raise => strict?, :default => 0 do |js|
          js["DELETED"].to_s.to_i
        end
      end
    end

    def asset_type_update! atype, options = {}
      name = validate_name case atype.class
      when ::Collins::AssetType
        atype.name
      else
        atype
      end

      parameters = {
        :name => options[:name],
        :label => options[:label]
      }
      if not ::Collins::Api::AssetType.asset_type_test then
        parameters = select_non_empty_parameters parameters
      end
      logger.debug("Updating asset type with name #{name} params #{parameters}")
      http_post("/api/assettype/#{name}", parameters) do |r|
        parse_response r, :expects => 200, :as => :status, :raise => strict?, :default => false
      end
    end

    def asset_type_get name
      name = validate_name name
      logger.debug("Fetching asset type with name #{name}")
      http_get("/api/assettype/#{name}") do |r|
        empty = ::Collins::AssetType.new({})
        parse_response r, :expects => 200, :as => :data, :default => empty, :raise => false do |js|
          ::Collins::AssetType.from_json(js)
        end
      end
    end

    def asset_type_get_all
      http_get("/api/assettypes") do |r|
        parse_response r, :expects => 200, :as => :data, :default => [], :raise => false do |js|
          js.map do |atype|
            ::Collins::AssetType.from_json(atype)
          end
        end
      end
    end

    private
    def validate_name name
      if ::Collins::Api::AssetType.asset_type_test then
        return name
      end
      name_opt = ::Collins::Option(name).map {|x| x.to_s.strip}.filter_not {|x| x.empty?}.filter {|x|
        x.size > 1 && x.size <= 32
      }
      if name_opt.empty? then
        raise ::Collins::ExpectationFailedError.new("name must be between 2 and 32 characters")
      else
        name_opt.get
      end
    end

  end # end module AssetType

end; end
