require 'collins/address'
require 'collins/ipmi'
require 'collins/power'
require 'collins/state'
require 'date'

module Collins

  # Represents the basic notion of a collins asset
  class Asset

    # Default time format when displaying dates associated with an asset
    DATETIME_FORMAT = "%F %T"

    # Asset finder related parameter descriptions
    # @note these exist here instead of the API module for convenience
    module Find
      # Find API parameters that are dates
      # @return [Array<String>] Date related query parameters
      DATE_PARAMS = [
        "createdAfter", "createdBefore", "updatedAfter", "updatedBefore"
      ]
      # Find API parameters that are not dates
      # This list exists so that when assets are being queries, we know what keys in the find hash
      # are attributes of the asset (such as hostname), and which are nort (such as sort or page).
      # @return [Array,<String>] Non-date related query parameters that are 'reserved'
      GENERAL_PARAMS = [
        "details", "tag", "type", "status", "page", "size", "sort", "state", "operation", "remoteLookup"
      ]
      # @return [Array<String>] DATE_PARAMS plus GENERAL_PARAMS
      ALL_PARAMS = DATE_PARAMS + GENERAL_PARAMS

      class << self
        def to_a
          Collins::Asset::Find::ALL_PARAMS
        end
        def valid? key
          to_a.include?(key.to_s)
        end
      end
    end

    include Collins::Util

    # @return [Array<CollinsAddress>] Addresses associated with asset
    attr_reader :addresses
    # @return [DateTime,NilClass] Timestamp. Can be nil
    attr_reader :created, :updated, :deleted
    # @return [Fixnum] Asset ID or 0
    attr_reader :id
    # @return [Collins::Ipmi] IPMI information
    attr_reader :ipmi
    # @return [String] multi-collins location
    attr_reader :location
    # @return [Collins::Power] Power configuration information
    attr_reader :power
    # @return [Collins::AssetState] Asset state, or nil
    attr_reader :state
    # @return [String] Asset status, or empty string
    attr_reader :status
    # @return [String] Asset tag, or empty string
    attr_reader :tag
    # @return [String] Asset type, or empty string
    attr_reader :type
    # @return [Hash] All additional asset metadata
    attr_reader :extras

    class << self
      # Given a Hash deserialized from JSON, convert to an Asset
      # @param [Hash] json_hash Asset representation
      # @param [Boolean] bare_asset Exists for API compatability, largely not needed
      # @return [Collins::Asset] The asset
      # @raise [Collins::CollinsError] If the specified hash is invalid
      def from_json json_hash, bare_asset = false
        (raise Collins::CollinsError.new("Invalid JSON specified for Asset.from_json")) if (json_hash.nil? || !json_hash.is_a?(Hash))
        json = deep_copy_hash json_hash
        json = if json["data"] then json["data"] else json end
        if bare_asset or !json.include?("ASSET") then
          asset = Collins::Asset.new json
        else
          asset = Collins::Asset.new json.delete("ASSET")
        end
        asset.send('ipmi='.to_sym, Collins::Ipmi.from_json(json.delete("IPMI")))
        asset.send('addresses='.to_sym, Collins::Address.from_json(json.delete("ADDRESSES")))
        asset.send('power='.to_sym, Collins::Power.from_json(json.delete("POWER")))
        asset.send('location=', json.delete("LOCATION"))
        asset.send('extras=', json)
        asset
      end

      # Convenience method for parsing asset ISO8601 date times
      # @param [String] s the ISO8601 datetime
      # @return [DateTime]
      def format_date_string s
        parsed = DateTime.parse(s)
        parsed.strftime("%FT%T")
      end
    end

    # Create an Asset
    # @param [Hash] opts Asset parameters
    # @option opts [String] :tag The asset tag
    # @option opts [String] :created The creation DateTime
    # @option opts [Fixnum] :id The ID of the asset
    # @option opts [String] :status The asset status
    # @option opts [String] :type The asset type
    # @option opts [String] :updated The update DateTime
    # @option opts [String] :deleted The delete DateTime
    def initialize opts = {}
      @extras = {}
      @addresses = []
      if opts.is_a?(String) then
        model = {:tag => opts}
      else
        model = opts
      end
      hash = symbolize_hash(model).inject({}) do |result, (k,v)|
        result[k.downcase] = v
        result
      end
      @created = parse_datetime hash.delete(:created).to_s
      @id = hash.delete(:id).to_s.to_i
      @status = hash.delete(:status).to_s
      @tag = hash.delete(:tag).to_s
      @type = hash.delete(:type).to_s
      @state = Collins::AssetState.from_json(hash.delete(:state))
      @updated = parse_datetime hash.delete(:updated).to_s
      @deleted = parse_datetime hash.delete(:deleted).to_s
      hash.each {|k,v| @extras[k] = v}
    end

    # @return [Collins::Address,NilClass] First available backend address
    def backend_address
      backend_addresses.first if backend_address?
    end
    # @return [Boolean] True if asset has a backend address
    def backend_address?
      backend_addresses.length > 0
    end
    # @return [Array<Collins::Address>] Array of backend addresses
    def backend_addresses
      addresses.select{|a| a.is_private?}
    end

    # @deprecated Users are encouraged to use {#backend_address}
    # @return [String,NilClass] Address of first available backend address
    def backend_ip_address
      backend_address.address if backend_address?
    end
    # @deprecated Users are encouraged to uses {#backend_addresses}
    # @return [Array<String>] Backend IP addresses
    def backend_ip_addresses
      backend_addresses.map{|a| a.address}
    end

    # @return [Collins::Address,NilClass] First available public address
    def public_address
      public_addresses.first if public_address?
    end
    # @return [Boolean] True if asset has a public address
    def public_address?
      public_addresses.length > 0
    end
    # @return [Array<Collins::Address>] Array of public addresses
    def public_addresses
      addresses.select{|a| a.is_public?}
    end

    # @deprecated Users are encouraged to use {#public_address}
    # @return [String,NilClass] Address of first available public address
    def public_ip_address
      public_address.address if public_address?
    end
    # @deprecated Users are encouraged to uses {#public_addresses}
    # @return [Array<String>] Public IP addresses
    def public_ip_addresses
      public_addresses.map{|a| a.address}
    end

    # @return [String,NilClass] Netmask of first available backend address
    def backend_netmask
      backend_address.netmask if backend_address?
    end
    # @return [Array<String>] Array of backend netmasks
    def backend_netmasks
      backend_addresses.map{|i| i.netmask}
    end

    # Return the gateway address for the specified pool, or the first gateway
    # @note If there is no address in the specified pool, the gateway of the first usable address is
    # used, which may not be desired.
    # @param [String] pool The address pool to find a gateway on
    # @return [String] Gateway address, or nil
    def gateway_address pool = "default"
      address = addresses.select{|a| a.pool == pool}.map{|a| a.gateway}.first
      return address if address
      if addresses.length > 0 then
        addresses.first.gateway
      else
        nil
      end
    end

    # @return [Object,NilClass] See {#method_missing}
    def get_attribute name
      extract(extras, "ATTRIBS", "0", name.to_s.upcase)
    end

    # @return [Fixnum] Number of CPU's found
    def cpu_count
      (extract(extras, "HARDWARE", "CPU") || []).length
    end
    # @return [Array<Hash>] CPU information
    def cpus
      extract(extras, "HARDWARE", "CPU") || []
    end

    # @return [Array<Hash>] Disk information
    def disks
      extract(extras, "HARDWARE", "DISK") || []
    end
    # @return [Array<Hash>] Memory information
    def memory
      extract(extras, "HARDWARE", "MEMORY") || []
    end

    # @return [Array<Hash>] NIC information
    def nics
      extract(extras, "HARDWARE", "NIC") || []
    end
    # @return [Fixnum] Number of physical interfaces
    def physical_nic_count
      nics.length
    end
    # @return [Array<String>] MAC addresses associated with assets
    def mac_addresses
      nics.map{|n| n["MAC_ADDRESS"]}.select{|a| !a.nil?}
    end

    # @return [String] Human readable asset with no meta attributes
    def to_s
      updated_t = format_datetime(updated, "Never")
      created_t = format_datetime(created, "Never")
      ipmi_i = ipmi.nil? ? "No IPMI Data" : ipmi.to_s
      "Asset(id = #{id}, tag = #{tag}, status = #{status}, type = #{type}, created = #{created_t}, updated = #{updated_t}, ipmi = #{ipmi_i}, state = #{state.to_s})"
    end

    def respond_to? name
      if extract(extras, "ATTRIBS", "0", name.to_s.upcase).nil? then
        super
      else
        true
      end
    end

    protected
    # We do not allow these to be externally writable since we won't actually update any of the data
    attr_writer :addresses, :created, :id, :ipmi, :location, :power, :state, :status, :tag, :type, :updated, :extras

    # Convenience method for {#get_attribute}
    #
    # This 'magic' method allows you to retrieve attributes on an asset, or check if an attribute
    # exists via a predicate method.
    #
    # @example
    #   real_asset.hostname # => "foo"
    #   bare_asset.hostname # => nil
    #   real_asset.hostname? # => true
    #   bare_asset.hostname? # => false
    #
    # @note This is never called directly
    # @return [NilClass,Object] Nil if attribute not found, otherwise the attribute value
    def method_missing(m, *args, &block)
      name = m.to_s.upcase
      is_bool = name.end_with?('?')
      if is_bool then
        name = name.sub('?', '')
        respond_to?(name)
      else
        extract(extras, "ATTRIBS", "0", name)
      end
    end

    def parse_datetime value
      return nil if (value.nil? or value.empty?)
      DateTime.parse value
    end

    def format_datetime value, default
      if value then
        value.strftime(DATETIME_FORMAT)
      else
        default
      end
    end

    # Convenience method for finding something in a (potentially) deep hash
    def extract(hash, *args)
      begin
        tmp = hash
        args.each do |arg|
          tmp = tmp[arg]
        end
        tmp
      rescue
        nil
      end
    end

  end
end
