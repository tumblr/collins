module CollinsShell; module Console; module Commands
  Io = Pry::CommandSet.new do
    create_command "wc", "Pipe results to wc to get the number of assets/lines" do
      command_options :keep_retval => true

      group "I/O"

      def process
        args.size
      end
    end

    create_command "more", "Ensure results are paginated" do
      command_options :keep_retval => true

      group "I/O"

      def process
        value = args.map {|a| a.to_s}.join("\n")
        render_output value, opts
        nil
      end
    end

  end
end; end; end
