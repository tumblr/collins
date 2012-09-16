module Collins; module Api

  module AssetState
    def state_test; @state_test end
    def state_test= v; @state_test = v end
    module_function :state_test, :state_test=

    def state_create! name, label, description, status = nil
      name = validate_state_name name
      parameters = {
        :label => label,
        :description => description,
        :status => status
      }
      if not ::Collins::Api::AssetState.state_test then
        parameters = select_non_empty_parameters parameters
      end
      logger.debug("Creating state with name #{name}")
      http_put("/api/state/#{name}", parameters) do |r|
        parse_response r, :expects => 201, :as => :status, :raise => strict?, :default => false
      end
    end
    def state_delete! name
      name = validate_state_name name
      logger.debug("Deleting state with name #{name}")
      http_delete("/api/state/#{name}") do |r|
        parse_response r, :expects => 202, :as => :data, :raise => strict?, :default => 0 do |js|
          js["DELETED"].to_s.to_i
        end
      end
    end
    def state_update! name, options = {}
      name = validate_state_name name
      parameters = {
        :label => options[:label],
        :description => options[:description],
        :name => options[:name],
        :status => options[:status]
      }
      if not ::Collins::Api::AssetState.state_test then
        parameters = select_non_empty_parameters parameters
      end
      logger.debug("Updating state with name #{name} params #{parameters}")
      http_post("/api/state/#{name}", parameters) do |r|
        parse_response r, :expects => 200, :as => :status, :raise => strict?, :default => false
      end
    end
    def state_get name
      name = validate_state_name name
      logger.debug("Fetching state with name #{name}")
      http_get("/api/state/#{name}") do |r|
        empty = ::Collins::AssetState.new({})
        parse_response r, :expects => 200, :as => :data, :default => empty, :raise => false do |js|
          ::Collins::AssetState.from_json(js)
        end
      end
    end
    def state_get_all
      http_get("/api/states") do |r|
        parse_response r, :expects => 200, :as => :data, :default => [], :raise => false do |js|
          js.map do |state|
            ::Collins::AssetState.from_json(state)
          end
        end
      end
    end

    private
    def validate_state_name name
      if ::Collins::Api::AssetState.state_test then
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

  end # end module AssetState

end; end
