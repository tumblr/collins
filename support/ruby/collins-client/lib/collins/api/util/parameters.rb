module Collins; module Api; module Util

  module Parameters
    protected

    # retrieve a key from a hash or use the default
    def get_option key, hash, default
      if hash.include?(key) then
        hash[key]
      elsif hash.include?(key.to_s) then
        hash[key.to_s]
      else
        default
      end
    end

    def get_page_options options = {}
      {
        :page => get_option(:page, options, 0),
        :size => get_option(:size, options, 25),
        :sort => get_option(:sort, options, "DESC")
      }
    end

    # select parameters from a hash matching some options
    def select_non_empty_parameters params
      select_parameters params, :nil => false, :empty_string => false
    end
    def select_parameters params, options = {}
      params.inject({}) do |result, (k,v)|
        if v.nil? && options[:nil] == false then
          # don't include
        elsif v.is_a?(String) && v.empty? && options[:empty_string] == false then
          # don't include
        else
          result[k] = v
        end
        result
      end
    end

  end # Parameters module

end; end; end
