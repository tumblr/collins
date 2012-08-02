require 'pry'

module CollinsShell; module Console; module Commands
  Cat = Pry::CommandSet.new do
    create_command "cat", "Output data associated with the specified asset" do

      include CollinsShell::Console::CommandHelpers

      group "I/O"

      def options(opt)
        opt.banner <<-BANNER
          Usage: cat [-l|--logs] [-b|--brief] [--help]

          Cat the specified asset or log.

          If you are in an asset context, cat does not require the asset tag. In an asset context you can do:
            cat                               # no-arg displays current asset
            cat -b                            # short-display
            cat -l                            # table formatted logs
            cat /var/log/SEVERITY             # display logs of a specified type
            cat /var/log/messages             # all logs
          If you are not in an asset context, cat requires the tag of the asset you want to display.
            cat                               # display this help
            cat asset-tag                     # display asset
            cat -b asset-tag                  # short-display
            cat -l asset-tag                  # table formatted logs
            cat /var/log/assets/asset-tag     # display logs for asset
            cat /var/log/hosts/hostname       # display logs for host with name
        BANNER
        opt.on :b, "brief", "Brief output, not detailed"
        opt.on :l, "logs", "Display logs as well"
      end

      def process
        stack = _pry_.binding_stack
        if args.first.to_s.start_with?('/var/log/') then
          display_logs args.first, stack
        else
          tag = resolve_asset_tag args.first, stack
          if tag.nil? then
            run "help", "cat"
            return
          end
          display_asset tag
        end
      end

      # Given a logfile specification, parse it and render it
      def display_logs logfile, stack
        rejects = ['var', 'log']
        paths = logfile.split('/').reject{|s| s.empty? || rejects.include?(s)}
        if paths.size == 2 then
          display_sub_logs paths[0], paths[1]
        elsif paths.size == 1 then
          display_asset_logs tag_from_stack(stack), paths[0]
        else
          run "help", "cat"
        end
      end

      # Render logs of the type `/var/log/assets/TAG` or `/var/log/hosts/HOSTNAME`
      def display_sub_logs arg1, arg2
        if arg1 == 'assets' then
          display_asset_logs arg2, 'messages'
        elsif arg1 == 'hosts' then
          asset = find_one_asset(['HOSTNAME', arg2])
          display_asset_logs asset, 'messages'
        else
          output.puts "#{text.bold('Invalid log type:')} Only 'assets' or 'hosts' are valid, found '#{arg1}'"
          output.puts
          run "help", "cat"
        end
      end

      # Render logs for an asset according to type, where type is 'messages' (all) or a severity
      def display_asset_logs asset_tag, type
        begin
          asset = Collins::Util.get_asset_or_tag(asset_tag).tag
        rescue => e
          output.puts "#{text.bold('Invalid asset:')} '#{asset_tag}' not valid - #{e}"
          return
        end
        severity = Collins::Api::Logging::Severity
        severity_level = severity.value_of type
        if type == 'messages' then
          get_and_print_logs asset_tag, "ASC"
        elsif not severity_level.nil? then
          get_and_print_logs asset_tag, "ASC", severity_level
        else
          message = "Only '/var/log/messages' or '/var/log/SEVERITY' are valid here"
          sevs = severity.to_a.join(', ')
          output.puts "#{text.bold('Invalid path specified:')}: #{message}"
          output.puts "Valid severity levels are: #{sevs}"
        end
      end

      def display_asset tag
        if asset_exists? tag then
          asset = get_asset tag
          show_logs = opts.logs?
          show_details = !opts.brief?
          show_color = Pry.color
          logs = []
          logs = call_collins("logs(#{tag})") {|c| c.logs(tag, :size => 5000)} if show_logs
          printer = CollinsShell::AssetPrinter.new asset, shell_handle, :logs => logs, :detailed => show_details, :color => show_color
          render_output printer.to_s
        else
          output.puts  "#{text.bold('No such asset:')} #{tag}"
        end
      end

      def get_and_print_logs asset_tag, sort, filter = nil, size = 5000
        logs = call_collins "logs(#{asset_tag})" do |client|
          client.logs asset_tag, :sort => sort, :size => size, :filter => filter
        end
        printer = CollinsShell::LogPrinter.new asset_tag, logs
        output.puts printer.to_s
      end

    end # create_command
  end # CommandSet
end; end; end
