require 'collins_shell/monkeypatch'
require 'collins_shell/util/asset_stache'

module CollinsShell
  module Util

    SIZABLE_ATTRIBUTES = [:memory_size_total, :disk_storage_total]

    def call_collins client, operation, &block
      begin
        block.call(client)
      rescue SystemExit => e
      end
    end

    def get_selector selector, tags, size, remote = false
      asset_params = ::Collins::Asset::Find.to_a.inject({}) {|res,el| res.update(el.to_s.upcase => el)}
      selector = symbolize_hash(selector).inject({}) do |result, (k,v)|
        upcase_key = k.to_s.upcase
        if asset_params.key?(upcase_key) then # normalized reserved params
          corrected_key = asset_params[upcase_key]
          if ::Collins::Asset::Find::DATE_PARAMS.include?(corrected_key) then
            result[corrected_key.to_sym] = ::Collins::Asset.format_date_string(v)
          else
            result[corrected_key.to_sym] = v
          end
        elsif upcase_key == k.to_s then # respect case
          result[k] = v
        else # otherwise downcase
          result[k.downcase] = v
        end
        result
      end
      if not selector.include?(:operation) then
        selector.update(:operation => 'and')
      end
      if not selector.include?(:remoteLookup) and remote then
        selector.update(:remoteLookup => remote.to_s)
      end
      if not selector.include?(:size) and size then
        selector.update(:size => size)
      end
      selector.update(:details => true) if is_array?(tags)
      CollinsShell::Util::SIZABLE_ATTRIBUTES.each do |attrib|
        attrib_value = selector[attrib]
        if attrib_value && attrib_value.to_s.is_disk_size? then
          selector.update(attrib => attrib_value.to_s.to_bytes)
        end
      end
      selector
    end

    def asset_exec asset, execs, confirm = true
      return unless execs
      mustache = CollinsShell::Util::AssetStache.new asset
      rendered = mustache.render "/bin/bash -c '#{execs}'"
      say_status("exec", rendered, :red)
      require_yes("Running on #{asset.tag}. ARE YOU SURE?", :red) if confirm
      system(rendered)
    end

    def asset_get tag, options
      call_collins get_collins_client, "get asset" do |client|
        as_asset = Collins::Asset.new(tag)
        as_asset.location = options.remote
        asset = client.get as_asset
      end
    end

    def print_find_results assets, tags, options = {}
      tags = [:tag,:status,:type,:created,:updated,:location] if not is_array?(tags)
      if options[:url] then
        tags << :url
      end
      if options[:header] then
        puts(tags.join(','))
      end
      [assets].flatten.each do |asset|
        puts format_asset_tags(asset, tags)
      end
    end

    def format_asset_tags asset, tags
      tags.map do |tag|
        if tag.to_s =~ /^ipmi_(.*)/ then
          asset.ipmi.send($1.to_sym)
        elsif tag.to_s.split('.').length == 2 then
          o, m = tag.to_s.split('.')
          result = asset.send(o.to_sym)
          if result.nil? then
            "nil"
          else
            format_asset_value result.send(m.to_sym)
          end
        elsif tag == :url then
          config = get_collins_config
          " #{config[:host]}/asset/#{asset.tag}"
        else
          result = asset.send(tag.to_sym)
          format_asset_value result
        end
      end.join(',')
    end

    def format_asset_value value
      if is_array? value then
        value.map {|v| format_asset_value v}
      elsif value.is_a?(Collins::Address) then
        [:address,:gateway,:netmask].map {|s| value.send(s)}.join('|')
      else
        value.to_s
      end
    end

    def is_array? tags
      tags && tags.is_a?(Array) && tags.length > 0
    end

  end
end
