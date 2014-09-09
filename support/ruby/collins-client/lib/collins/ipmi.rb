module Collins

  class Ipmi

    include Collins::Util

    attr_accessor :address, :asset_id, :gateway, :id, :netmask, :ipmipass, :ipmiuser

    def self.from_json json
      Collins::Ipmi.new json
    end

    def initialize opts = {}
      hash = symbolize_hash(opts).inject({}) do |result, (k,v)|
        key = k.to_s.downcase.sub(/^ipmi_/, "").to_sym
        result[key] = v
        result
      end
      @address = hash[:address].to_s
      @asset_id = hash[:asset_id].to_s.to_i
      @gateway = hash[:gateway].to_s
      @id = hash[:id].to_s.to_i
      @netmask = hash[:netmask].to_s
      @ipmipass = hash[:ipmipass].to_s
      @ipmiuser = hash[:ipmiuser].to_s
    end

    def empty?
      @id == 0
    end

    def to_s
      if empty? then
        "Ipmi(None)"
      else
        "Ipmi(id = #{id}, asset_id = #{asset_id}, address = #{address}, gateway = #{gateway}, netmask = #{netmask}, ipmiuser = #{ipmiuser}, ipmipass = #{ipmipass})"
      end
    end

  end
end
