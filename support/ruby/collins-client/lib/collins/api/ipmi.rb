
module Collins; module Api

  module Ipmi

    def ipmi_pools
      logger.debug("Finding IPMI address pools")
      http_get("/api/ipmi/pools") do |response|
        parse_response response, :expects => 200, :default => [], :raise => strict? do |json|
          json["data"]["POOLS"]
        end
      end
    end

  end # module Ipmi

end; end
