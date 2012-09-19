require 'timeout'
require 'uri'

module Collins; module Api; module Util

  module Requests

    protected
    def http_get uri, parameters = {}, remote = nil
      http_call(uri) {
        if parameters.is_a?(Array) then
          params = {:query => parameters.join("&")}
        else
          params = {:query => parameters}
        end
        params = strip_request :query, params
        result = self.class.get(uri, http_options(params, remote))
        if block_given? then
          yield(result)
        else
          result
        end
      }
    end

    def http_put uri, parameters = {}, remote = nil
      http_call(uri) {
        params = strip_request :body, :body => parameters
        result = self.class.put(uri, http_options(params, remote))
        if block_given? then
          yield(result)
        else
          result
        end
      }
    end

    def http_post uri, parameters = {}, remote = nil
      http_call(uri) {
        params = strip_request :body, :body => parameters
        result = self.class.post(uri, http_options(params, remote))
        if block_given? then
          yield(result)
        else
          result
        end
      }
    end

    def http_delete uri, parameters = {}, remote = nil
      http_call(uri) {
        params = strip_request :body, :body => parameters
        result = self.class.delete(uri, http_options(params, remote))
        if block_given? then
          yield(result)
        else
          result
        end
      }
    end

    def http_options opts = {}, remote = nil
      if remote then
        host_info = get_location_information remote
        auth = {:username => host_info.username, :password => host_info.password}
        base_uri = host_info.host
      else
        auth = {:username => username, :password => password}
        base_uri = host
      end
      http_opts = opts.merge!({:basic_auth => auth, :base_uri => base_uri, :timeout => timeout_i})
      http_opts[:headers] = headers unless headers.empty?
      http_opts[:debug_output] = $stderr if (logger.level < 0 and Module.const_defined?(:HTTP_DEBUG) and HTTP_DEBUG)
      http_opts
    end

    def http_call uri, &block
      trace("Calling uri #{uri}, waiting for #{timeout_i} seconds")
      begin
        timeout(timeout_i) {
          block.call
        }
      rescue Timeout::Error => e
        raise Timeout::Error.new("Timeout talking to #{uri}: #{e}")
      rescue Collins::RequestError => e
        e.uri = uri
        raise e
      rescue Exception => e
        if e.class.to_s == "WebMock::NetConnectNotAllowedError" then
          raise e
        else
          raise e.class.new("Exception talking to #{uri}: #{e}")
        end
      end
    end

    def strip_request key, options
      if options[key] && options[key].empty? then
        {}
      else
        options
      end
    end

    def get_location_information location
      location_s = location.downcase.to_sym
      return locations[location_s] if locations[location_s]
      logger.debug("Fetching credentials for location #{location_s}")
      http_get("/api/asset/#{location}") do |response|
        result = parse_response response, :expects => 200, :as => :asset, :raise => strict?, :default => nil
        if result && result.get_attribute(:location) then
          if result.get_attribute(:location).nil? then
            raise AuthenticationError.new("Could not find LOCATION attribute on asset #{location}")
          end
          uri = URI(result.get_attribute(:location))
          if uri.port then
            port = ":#{uri.port}"
          else
            port = ""
          end
          locations[location_s] = OpenStruct.new Hash[
            :username => uri.user,
            :password => uri.password,
            :host => "#{uri.scheme}://#{uri.host}#{port}"
          ]
        else
          raise AuthenticationError.new("Could not find LOCATION attribute on asset #{location}")
        end
      end
      locations[location_s]
    end


  end # module Requests

end; end; end
