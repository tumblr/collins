module Collins
  class Asset
    # Params we know about for updates, others come in via attribute hash
    module Update

      NON_ATTRIBUTE_PARAMS = [
        "CHASSIS_TAG", "RACK_POSITION", /^POWER_(.*)_(.*)/i
      ]
      FILE_PARAMS = [
        "lshw", "lldp"
      ]
      ALL_PARAMS = NON_ATTRIBUTE_PARAMS + FILE_PARAMS

      class << self

        def to_a
          Collins::Asset::Update::ALL_PARAMS
        end

        def get_param_value key, value
          if is_file_param?(key) then
            if value.start_with?('@') then
              filename = File.expand_path(value[1..-1])
              if !File.readable?(filename) then
                msg = "Could not read file '#{filename}' for key '#{key}'"
                raise ::Collins::ExpectationFailedError.new msg
              else
                File.read(filename)
              end
            else
              value
            end
          else
            value
          end
        end # get_param_value

        def get_param key
          to_a.each do |k|
            if k.is_a?(Regexp) && !k.match(key).nil? then
              # Assume it's a power setting until we have >1 regexp
              return key.upcase
            elsif key.to_s.downcase == k.to_s.downcase then
              return k
            end
          end
          return key
        end # get_param

        def is_file_param? key
          FILE_PARAMS.map{|k|k.to_s.downcase}.include?(key.to_s.downcase)
        end # is_file_param?

        def is_attribute? key
          to_a.each do |k|
            if k.is_a?(Regexp) && !k.match(key).nil? then
              return false
            elsif key.to_s.downcase == k.to_s.downcase then
              return false
            end
          end
          return true
        end # is_attribute?

      end # class << self

    end # module Update
  end # class Asset
end # module Collins
