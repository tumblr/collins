module Collins

  class Address
    include Collins::Util

    attr_accessor :id, :asset_id, :address, :gateway, :netmask, :pool

    class << self
      def from_json json
        return [] if json.nil? || json.empty?
        if not json.is_a?(Array) then
          json = [json]
        end
        json.map { |j| Collins::Address.new j }
      end

      def is_private? address
        if address =~ /^10\./ then
          true
        elsif address =~ /^192\.168\./ then
          true
        elsif address =~ /^172\.(?:1[6-9]|2[0-9]|3[0-1])\./ then
          true
        else
          false
        end
      end
      def is_public? address
        not is_private?(address)
      end
    end

    def initialize model = {}
      hash = symbolize_hash(model).inject({}) do |result, (k,v)|
        result[k.downcase] = v
        result
      end
      @id = hash[:id].to_s.to_i
      @asset_id = hash[:asset_id].to_s.to_i
      @address = hash[:address].to_s
      @gateway = hash[:gateway].to_s
      @netmask = hash[:netmask].to_s
      @pool = hash[:pool].to_s
    end

    def is_addressable?
      @address.length > 0
    end

    def is_private?
      Collins::Address.is_private? @address
    end

    def is_public?
      is_addressable? and not is_private?
    end

    def to_hash
      {
        :address => @address,
        :gateway => @gateway,
        :netmask => @netmask,
        :pool => @pool,
        :is_private => is_private?,
        :is_public => is_public?
      }
    end
    def to_s
      "Collins::Address(address = %{address}, gateway = %{gateway}, netmask = %{netmask}, is_private = %{is_private})" % to_hash
    end

  end

end
