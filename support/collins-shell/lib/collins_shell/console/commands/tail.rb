require 'pry'
require 'set'

module CollinsShell; module Console; module Commands
  Tail = Pry::CommandSet.new do
    create_command "tail", "Print the last lines of a log file" do

      include CollinsShell::Console::CommandHelpers

      group "I/O"

      def setup
        @lines = 10
        @follow = false
        @got_flags = false
        @sleep = 10
        @test = false
      end

      def integer_arg(opt, short, long, description, &block)
        opt.on short, long, description, :argument => true, :as => :integer do |i|
          if i < 1 then
            raise ArgumentError.new("Missing a required argument for --#{long}")
          end
          @got_flags = true
          block.call(i)
        end
      end

      def options(opt)
        opt.banner <<-BANNER
          Usage: tail [OPTION] [FILE]
                 tail --follow [FILE]
                 tail --lines N [FILE]
                 tail --sleep N [FILE]

          Tail the specified log.

          If you are in an asset context, tail does not require the asset tag. In an asset context you can do:
            tail                                   # display this help
            tail -n 10                             # same as tail -n 10 /var/log/messages
            tail -f                                # same as tail -f /var/log/messages
            tail [-n|-f] /var/log/SEVERITY         # tail /var/log/SEVERITY
            tail /var/log/messages                 # last 10 messages
            tail -n 10 /var/log/messages           # last 10 messages
            tail -f /var/log/messages              # follow log messages
          If you are not in an asset context, log requires the tag of the asset you want to display.
            tail                                   # display this help
            tail [-n|-f] asset-tag                 # same as tail in asset context
            tail [-n|-f] /var/log/messages         # show logs for all assets (requires permission)
            tail [-n|-f] /var/log/assets/asset-tag # same as tail in asset context
            tail [-n|-f] /var/log/hosts/hostname   # same as tail in asset context, but finds host
        BANNER
        opt.on :f, "follow", "Output appended data as file grows" do
          @got_flags = true
          @follow = true
        end
        opt.on :t, :test, "Show logs that have already been seen" do
          @got_flags = true
          @test = true
        end
        integer_arg(opt, :n, :lines, "Show the last n lines of the file") do |i|
          @lines = i
        end
        integer_arg(opt, :s, :sleep, "Sleep this many seconds between pools") do |i|
          @sleep = i
        end
      end

      def process
        stack = _pry_.binding_stack
        if args.first.to_s.start_with?('/var/log/') then
          display_logs args.first, stack
        else
          tag = args.first.to_s.strip
          if tag.empty? then
            if asset_context?(stack) and @got_flags then
              display_asset_logs tag_from_stack(stack), 'messages'
            else
              run "help", "tail"
              return
            end
          else
            display_asset_logs tag, 'messages'
          end
        end
      end

      # Given a logfile specification, parse it and render it
      def display_logs logfile, stack
        rejects = ['var', 'log']
        paths = logfile.split('/').reject{|s| s.empty? || rejects.include?(s)}
        if paths.size == 2 then
          display_sub_logs paths[0], paths[1]
        elsif paths.size == 1 then
          display_asset_logs (tag_from_stack(stack) || 'all'), paths[0]
        else
          run "help", "tag"
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
          run "help", "tag"
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
          get_and_print_logs asset_tag
        elsif not severity_level.nil? then
          get_and_print_logs asset_tag, severity_level
        else
          message = "Only '/var/log/messages' or '/var/log/SEVERITY' are valid here"
          sevs = severity.to_a.join(', ')
          output.puts "#{text.bold('Invalid path specified:')}: #{message}"
          output.puts "Valid severity levels are: #{sevs}"
        end
      end

      def all? tag
        tag.to_s.downcase == 'all'
      end

      def get_and_print_logs asset_tag, filter = nil
        size = @lines
        if not @follow then
          logs = call_collins "logs(#{asset_tag})" do |client|
            client.logs asset_tag, :sort => "DESC", :size => size, :filter => filter, :all_tag => 'all'
          end.reverse
          printer = CollinsShell::LogPrinter.new asset_tag, :logs => logs, :all_tag => 'all'
          output.puts printer.to_s
        else
          seen = Set.new
          printer = CollinsShell::LogPrinter.new asset_tag, :streaming => true, :all_tag => 'all'
          while true do
            logs = call_collins "logs(#{asset_tag})" do |client|
              client.logs asset_tag, :sort => "DESC", :size => size, :filter => filter, :all_tag => 'all'
            end.reverse
            unseen_logs = select_logs_for_follow seen, logs
            if unseen_logs.size > 0 then
              output.puts printer.render(unseen_logs)
            end
            sleep(@sleep)
          end # while true
        end # else for follow
      end

      def select_logs_for_follow seen_set, logs
        if @test then
          logs
        else
          unseen_logs = logs.reject {|l| seen_set.include?(l.to_s.hash)}
          unseen_logs.each {|l| seen_set << l.to_s.hash}
          unseen_logs
        end
      end

    end # create_command
  end # CommandSet
end; end; end
