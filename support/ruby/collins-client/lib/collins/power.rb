require 'collins/errors'

module Collins

  class InvalidPowerStatus < CollinsError; end

  class PowerUnit
    include Collins::Util

    attr_accessor :key, :value, :type, :label, :position, :is_required, :unique

    def initialize model = {}
      hash = symbolize_hash(model).inject({}) do |result, (k,v)|
        result[k.downcase] = v
        result
      end
      @key = hash[:key].to_s
      @value = hash[:value].to_s
      @type = hash[:type].to_s
      @label = hash[:label].to_s
      @position = hash[:position].to_s.to_i
      @is_required = hash[:is_required]
      @unique = hash[:unique]
    end

    def is_required?
      @is_required == true
    end
    def unique?
      @unique == true
    end
  end

  class Power

    include Collins::Util

    attr_accessor :unit_id, :units

    class << self
      def from_json json
        return [] if (json.nil? or json.empty?)
        if not json.is_a?(Array) then
          json = [json]
        end
        json.map { |j| Collins::Power.new j }
      end
      def normalize_action action
        case action.to_s.downcase.to_sym
        when :off, :poweroff
          "powerOff"
        when :on, :poweron
          "powerOn"
        when :powersoft
          "powerSoft"
        when :soft, :rebootsoft
          "rebootSoft"
        when :hard, :reboothard
          "rebootHard"
        when :status, :powerstate
          "powerState"
        when :verify
          "verify"
        when :identify
          "identify"
        else
          raise InvalidPowerStatus.new("#{action} is not a valid power status")
        end
      end
    end

    def initialize model = {}
      hash = symbolize_hash(model).inject({}) do |result, (k,v)|
        result[k.downcase] = v
        result
      end
      @unit_id = hash[:unit_id].to_s.to_i
      @units = (hash[:units] || []).map {|u| Collins::PowerUnit.new(u)}
    end

    def keys
      units.map{|u| u.key }
    end
    def values
      units.map{|u| u.value}
    end
    def types
      units.map{|u| u.type}
    end
    def labels
      units.map{|u| u.label}
    end
    def positions
      units.map{|u| u.position}
    end

  end

end
