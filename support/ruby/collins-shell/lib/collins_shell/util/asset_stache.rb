require 'mustache'

module CollinsShell; module Util

  class AssetStache < Mustache
    attr_accessor :asset

    def initialize asset
      @asset = asset
    end

    def respond_to? meth
      result = asset.send(meth.to_sym)
      if result.nil? then
        super
      else
        result
      end
    end

    def method_missing meth, *args, &block
      result = asset.send(meth.to_sym)
      if result.nil? then
        super
      else
        result
      end
    end

  end

end; end
