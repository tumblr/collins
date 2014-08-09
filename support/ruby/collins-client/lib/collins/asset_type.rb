require 'ostruct'

module Collins
  class AssetType
    include Collins::Util

    attr_accessor :id, :label, :name

    def self.from_json json
      Collins::AssetType.new json
    end

    def initialize opts = {}
      hash = symbolize_hash(opts).inject({}) do |result, (k,v)|
        key = k.to_s.downcase.to_sym
        result[key] = v
        result
      end
      @id = hash[:id].to_s.to_i
      @label = hash[:label].to_s
      @name = hash[:name].to_s
    end

    def empty?
      @id == 0
    end

    def to_s
      if empty? then
        "AssetType(None)"
      else
        "AssetType(id = #{id}, name = '#{name}', label = '#{label}')"
      end
    end

  end
end
