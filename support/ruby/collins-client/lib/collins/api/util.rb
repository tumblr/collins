require 'collins/api/util/parameters'
require 'collins/api/util/requests'
require 'collins/api/util/responses'

module Collins; module Api

  module Util

    include Collins::Api::Util::Parameters
    include Collins::Api::Util::Requests
    include Collins::Api::Util::Responses


    #returns true if successful ping to collins, false otherwise
    def ping
      begin
        http_get("/api/ping") do |response|
          parse_response response, :expects => 200
        end
        true
      rescue
        false
      end
    end

  end # module Util

end; end
