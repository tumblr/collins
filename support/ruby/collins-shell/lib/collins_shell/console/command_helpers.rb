require 'collins_shell/console/cache'

module CollinsShell; module Console; module CommandHelpers

  include CollinsShell::Console::Cache

  # @return [Boolean] true if asset associated with specified tag exists
  def asset_exists? tag
    m = "asset_exists?(#{tag})"
    cache_get_or_else(m) {
      call_collins(m) {|c| c.exists?(tag)}
    }
  end

  # @return [Collins::Asset] associated with the specified tag
  def get_asset tag
    m = "get_asset(#{tag})"
    cache_get_or_else(m) {
      call_collins(m) {|c| c.get(tag)}
    }
  end

  # @return [Object] result of calling collins using the specifed block
  def call_collins operation, &block
    shell_handle.call_collins(collins_client, operation, &block)
  end
  # @return [Collins::Client] A Collins::Client instance
  def collins_client
    shell_handle.get_collins_client cli_options
  end

  # @return [Hash] CLI specified options
  def cli_options
    CollinsShell::Console.options
  end

  # @return [Array<Collins::Asset>,NilClass]
  def find_one_asset tags_values, details = true
    find_assets(tags_values, details).first
  end

  # Given an array of keys and values, return matching assets
  # @param [Array<String>] tags_values an array where even elements are keys for a search and odd elements are values
  # @param [Boolean] details If true, return full assets, otherwise return a sorted array of tags
  # @return [Array<Collins::Asset>,Array<String>]
  def find_assets tags_values, details
    q = tags_values.each_slice(2).inject({}) {|h,o| h.update(o[0].to_s.to_sym => o[1])}
    q.update(:details => details)
    selector = shell_handle.get_selector(q, nil, 5000)
    call_collins("find(#{selector})") do |c|
      if details then
        c.find(selector)
      else
        c.find(selector).map{|a| a.tag}.sort
      end
    end
  end

  # @return [Array<String>,Array<Collins::Asset>] an array of values associated with the specified tag, or the asset associated with that tag if resolve asset is true.
  def get_tag_values tag, resolve_asset = true
    m = "get_tag_values(#{tag}, #{resolve_asset.to_s})"
    cache_get_or_else(m) {
      call_collins(m) {|c| c.get_tag_values(tag)}.sort
    }
  rescue Exception => e
    if e.is_a?(Collins::RequestError) then
      if e.code.to_i == 404 then
        [get_asset(tag)] if resolve_asset
      else
        raise e
      end
    else
      [get_asset(tag)] if resolve_asset
    end
  end

  def virtual_tags
    Collins::Asset::Find.to_a
  end

  # return known tags including virtual ones (ones not stored, but that can be used as
  # query parameters
  def get_all_tags include_virtual = true
    begin
      cache_get_or_else("get_all_tags(#{include_virtual.to_s})") {
        tags = call_collins("get_all_tags"){|c| c.get_all_tags}.map{|t|t.name}
        if include_virtual then
          [tags + Collins::Asset::Find.to_a].flatten.sort
        else
          tags.sort
        end
      }
    rescue Exception => e
      puts "Error retrieving tags: #{e}"
      []
    end
  end

  # Given a possible string tag and the context stack, find the asset tag
  # @param [NilClass,String] tag a possible tag for use
  # @param [Array<Binding>] stack the context stack
  # @return [String,NilClass] Either the given tag, the tag on the top of the context stack, or nil if neither is available
  def resolve_asset_tag tag, stack
    Collins::Option(tag).map{|s| s.strip}.filter_not{|s| s.empty?}.get_or_else {
      tag_from_stack stack
    }
  end

  # @return [CollinsShell::Util] a handle on the CLI interface
  def shell_handle
    CollinsShell::Asset.new([], cli_options)
  end

  # @param [Array<Binding>] stack the context stack
  # @return [NilClass,String] the asset tag at the top of the stack, if it exists
  def tag_from_stack stack
    node = stack.last
    node = node.eval('self') unless node.nil?
    if node and node.asset? then
      node.console_asset.tag
    else
      nil
    end
  end

  # @return [Boolean] true if in asset context
  def asset_context? stack
    !tag_from_stack(stack).nil?
  end

end; end; end
