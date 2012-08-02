module Collins; module Api; module Util

  module Errors

    protected
    def handle_error response
      if response.code >= 400 && rich_error_response?(response) then
        raise RichRequestError.new(
          "Error processing request", response.code, error_response(response), error_details(response)
        )
      elsif response.code >= 400 && error_response?(response) then
        raise RequestError.new("Error processing request: #{error_response(response)}", response.code)
      elsif response.code == 401 then
        raise AuthenticationError.new("Invalid username or password")
      elsif response.code > 401 then
        raise RequestError.new("Response code was #{response.code}, #{response.to_s}", response.code)
      end
    end
    def rich_error_response? response
      if error_response?(response) then
        parsed = response.parsed_response
        parsed.key?("data") && parsed["data"].key?("details")
      else
        false
      end
    end
    def error_response? response
      begin
        parsed = response.parsed_response
        parsed["status"] && parsed["status"].include?("error")
      rescue Exception => e
        logger.warn("Could not determine if response #{response} was an error. #{e}")
        false
      end
    end
    def error_details response
      response.parsed_response["data"]["details"]
    end
    def error_response response
      response.parsed_response["data"]["message"]
    end

  end

end; end; end
