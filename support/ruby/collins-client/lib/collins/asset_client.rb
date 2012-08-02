module Collins

  # Convenience class for making collins calls for only a single asset
  class AssetClient

    def initialize asset, client, logger
      @asset = asset
      if asset.is_a?(Collins::Asset) then
        @tag = asset.tag
      else
        @tag = asset
      end
      @client = client
      @logger = logger
    end

    def to_s
      "AssetClient(asset = #{@tag}, client = #{@client})"
    end

    # Fill in the missing asset parameter on the dynamic method if needed
    #
    # If {Collins::Client} responds to the method, and the method requires an `asset_or_tag`, we
    # insert the asset specified during initialization into the args array. If the method does not
    # require an `asset_or_tag`, we simply proxy the method call as is. If {Collins::Client} does
    # not respond to the method, we defer to `super`.
    #
    # @example
    #   collins_client.get('some_tag')            # => returns that asset
    #   collins_client.with_asset('some_tag').get # => returns that same asset
    #
    # @note this method should never be called directly
    def method_missing meth, *args, &block
      if @client.respond_to?(meth) then
        method_parameters = @client.class.instance_method(meth).parameters
        asset_idx = method_parameters.find_index do |item|
          item[1] == :asset_or_tag
        end
        if asset_idx.nil? then
          @client.send(meth, *args, &block)
        else
          args_with_asset = args.insert(asset_idx, @tag)
          logger.debug("Doing #{meth}(#{args_with_asset.join(',')}) for #{@tag}")
          @client.send(meth, *args_with_asset, &block)
        end
      else
        super
      end
    end

    def respond_to? meth, include_private = false
      @client.respond_to?(meth)
    end

  end

end
