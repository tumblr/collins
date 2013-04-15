require 'collins_shell/console/asset'

module CollinsShell; module Console
  class Filesystem
    include CollinsShell::Console::CommandHelpers

    attr_accessor :console_asset, :stack # Using console_asset, don't want to steal asset

    def initialize optns = {}
      options = Collins::Util.symbolize_hash(optns)
      @console_asset = options.fetch(:console_asset, nil)
      @options = options
      @stack = options.fetch(:stack, [])
    end

    def root?
      @stack.size == 0
    end

    def asset?
      !console_asset.nil?
    end

    def path
      '/' + @stack.join('/')
    end

    def pop
      stk = @stack.dup
      stk.pop
      stk = [] if stk.nil?
      asset = resolve_location(stk) if stk.size > 0
      opts = @options.merge(:stack => stk, :console_asset => asset)
      CollinsShell::Console::Filesystem.new(opts)
    end

    def push ctxt
      stk = @stack.dup
      stk << ctxt
      asset = resolve_location stk
      if asset? and asset then
        raise StandardError.new("Can not nest assets in assets")
      end
      opts = @options.merge(:stack => stk, :console_asset => asset)
      CollinsShell::Console::Filesystem.new(opts)
    end

    def exclude_methods
      ['respond_to?','to_s','cput', 'id'].map{|s|s.to_sym}
    end

    def asset_methods exclude_non_scalar = false
      non_scalar = [:addresses, :backend_address, :backend_addresses, :backend_ip_addresses,
        :backend_netmasks, :cpus, :disks, :extras, :get_attribute, :mac_addresses, :memory, :nics,
        :power, :public_address, :public_addresses, :public_ip_addresses]
      asset_objects = {:ipmi => Collins::Ipmi, :state => Collins::AssetState}
      ex_methods = exclude_methods
      ex_methods += non_scalar if exclude_non_scalar
      Collins::Asset.public_instance_methods(false).map(&:to_sym).reject do |meth|
        ex_methods.include?(meth)
      end.map do |meth|
        if asset_objects.key?(meth) then
          asset_objects[meth].public_instance_methods(false).map(&:to_sym).reject do |meh|
            ex_methods.include?(meh) || meh.to_s =~ /=$/
          end.map do |meh|
            "#{meth}.#{meh}"
          end
        else
          [meth.to_s]
        end
      end.flatten.sort
    end

    def available_commands
      includes = ['clear_cache'] + pry_commands
      if asset? then
        excludes = exclude_methods
        cmds1 = CollinsShell::Console::Asset.public_instance_methods(false).reject{|i| excludes.include?(i.to_sym)}
        cmds2 = asset_methods.map{|m| "asset.#{m}"}.sort
        cmds1 + cmds2 + includes
      else
        includes
      end
    end

    def to_s
      "#{self.class}(path = '#{path}', asset? = '#{asset?}')"
    end

    protected
    def method_missing meth, *args, &block
      if asset? then
        a = @console_asset
        if a.respond_to?(meth) then
          a.send(meth, *args, &block)
        else
          Pry.output.puts("Command not found: #{meth}")
          cmds = available_commands.join(', ')
          Pry.output.puts("Available commands: #{cmds}")
        end
      elsif pry_commands.include?(meth) then
        arg = args.unshift(meth).join(' ')
        runner = CollinsShell::Console
        runner.run_pry_command(arg, :context => self, :binding_stack => [Pry.binding_for(self)])
      else
        Pry.output.puts("command not found: #{meth}")
      end
    end

    def pry_commands
      @pry_commands ||= Pry.commands.list_commands.select{|c| c.respond_to?(:to_sym)}.map{|c| c.to_sym}
    end

    # @return [Collins::Asset,NilClass] asset if context is one, nil otherwise
    # @raise [ExpectationFailedException] if invalid nesting is attempted
    def resolve_location stack
      context = stack.last
      path = "/#{stack.join('/')}"

      if get_all_tags.include?(context) then
        Pry.output.puts("Found tag: '#{context}'")
        nil
      elsif asset_exists?(context) then
        Pry.output.puts("Found asset: '#{context}'")
        CollinsShell::Console::Asset.new(context)
      elsif stack.size % 2 == 0 then # should be key/value, we know it's not root
        begin
          assets = find_assets(stack, false)
          if assets.size == 0 then
            raise StandardError.new("No assets in path #{path}")
          elsif assets.size == 1 then
            Pry.output.puts("Found asset #{assets[0]} in #{path}")
            CollinsShell::Console::Asset.new(assets[0])
          else
            Pry.output.puts("#{assets.size} assets in path #{path}")
            nil
          end
        rescue Exception => e
          raise StandardError.new("Invalid path #{path} - #{e}")
        end
      else
        raise StandardError.new("Path #{path} is invalid")
      end
    end

  end
end; end
