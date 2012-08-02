require 'pry'
require 'collins_shell/util/asset_stache'

module CollinsShell; module Console; module Commands
  Iterators = Pry::CommandSet.new do

    create_command "ls", "Find assets according to specific criteria" do
      include CollinsShell::Console::OptionsHelpers
      include CollinsShell::Console::CommandHelpers

      command_options :keep_retval => true, :takes_block => true
      group "Context"

      # --return (after printing also return value) and --flood (disable paging)
      def options opt
        opt.banner <<-BANNER
          Usage: ls [-d|--delimiter] [-g|--grep] [-F--format] [-f|--flood] [path]

          ls provides you information based on your current context (either a path or an asset)

          When in an asset, ls will show you available commands
          When in a path, ls will show you either assets that match the path or values appropriate for the path
          When in no context, will show you available tags (to use in a path)

          Examples:
            ls /HOSTNAME/.*dev.*
            ls /HOSTNAME/.*dev.* --format='{{hostname}} {{status}} {{tag}}' --grep=blake

          You can customize the default format used by ls (when applied to assets) by creating a ~/.pryrc file with contents like:

              Pry.config.default_asset_format = '{{tag}} {{hostname}} {{status}}'

          Where the rhs of the default_asset_format is the format you want to use
        BANNER
        pager_options opt
        opt.on :d, "delimiter", "Delimiter for use with --format, defaults to \\n", :argument => true
        opt.on :r, "return", "Return values as well as outputting them"
        opt.on :g, "grep", "A regular expression to provide to results", :argument => true, :optional => false
        opt.on :F, "format", "Provide a format for output", :argument => true, :optional => false
      end

      def process
        # If a pipe is being used, grab the first pipe command
        first_after_pipe = arg_string.split('|', 2)[1].to_s.split(' ').first
        # Take a /PATH/FORMAT and convert it to an array
        path = args.first.to_s.split(/\//).reject{|s| s.empty? || s == "|" }
        # Account for Filesystem context
        stack  = _pry_.binding_stack.dup
        fs_node = stack.last.eval('self')
        # Are we just getting a naked query?
        display_commands = (fs_node.asset? && args.empty?)
        # Doing an ls in the stack, relative
        if not fs_node.root? and not arg_string.start_with?('/') then
          path.each do |context|
            case context.chomp
            when "", "." # blank is empty root node, . is self. Do nothing
              next
            when ".." # Up one level
              fs_node = fs_node.pop
            else
              begin
                fs_node = fs_node.push(context)
              rescue Exception => e
                output.puts("#{text.bold('Could not check context:')} #{e}")
                raise e
              end
            end
          end
          path = fs_node.stack
        end
        if command_block || get_format then
          details = true
        else
          details = false
        end
        # Should we just display commands?
        if display_commands then
          output.puts("Available commands:")
          process_values fs_node.available_commands, 8
          output.puts()
        # If we have nothing, grab all tags
        elsif path.size == 0 then
          value = get_all_tags
          process_values value
        # If we have an odd number, the last one is a tag so grab the values
        # for that tag
        elsif path.size % 2 == 1 then
          if virtual_tags.include?(path.last) then
            value = ["virtual tags have no values"]
          else
            value = get_tag_values(path.last) || []
          end
          process_values value
        # If we have an even number, grab assets that have these tag/values
        else
          assets = find_assets(path, details)
          process_values assets, 6
        end
      end

      # Handle faking unix pipes to commands, block invocation, and printing
      def process_values init_value, size = 4
        should_return = opts.return?
        cmd = arg_string.split('|',2)

        if cmd.size == 2 then
          cmds = cmd.last.split('|').map{|s| s.strip}
        else
          cmds = []
        end

        formatted = init_value
        formatter = nil
        if opts.format? then
          formatter = opts[:format]
        elsif Pry.config.default_asset_format then
          if formatted.any? {|o| o.is_a?(Collins::Asset)} then
            formatter = Pry.config.default_asset_format
          end
        end

        if formatter then
          formatted = formatted.map do |v|
            a = CollinsShell::Util::AssetStache.new(v)
            a.render formatter
          end
        end

        grep_regex = Regexp.new(opts[:g] || ".")
        if formatted.respond_to?(:grep) then
          formatted = formatted.grep(grep_regex)
        end

        if cmds.size > 0 then
          results = formatted
          while cmd = cmds.shift do
            results = run(cmd, results).retval
          end
          value = results
          value_s = results.to_s
        else
          value = formatted
          if formatter then
            delim = Collins::Option(opts[:delimiter]).get_or_else("\n")
            value_s = value.join(delim)
          else
            value_s = format_values(value, size)
          end
        end
        if command_block then
          command_block.call(value)
        else
          # Handle commands like 'more' that should not return a value
          if not value.nil? and not value_s.empty? then
            render_output value_s, opts
          end
          value if should_return
        end
      end

      def get_format
        if opts.format? then
          opts[:format]
        elsif Pry.config.default_asset_format then
          Pry.config.default_asset_format
        else
          nil
        end
      end

      def format_values array, width = 4
        return "" if array.empty?
        t = Terminal::Table.new
        t.style = {:border_x => "", :border_y => "", :border_i => ""}
        line = []
        array.each do |o|
          line << o
          if line.size >= width then
            t << line
            line = []
          end
        end
        if not line.empty? then
          t << line
        end
        t.to_s
      end
    end
  end # CommandSet
end; end; end
