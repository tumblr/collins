require 'pry'

module CollinsShell; module Console; module Commands
  Versions = Pry::CommandSet.new do
    create_command "latest", "Latest version of collins shell" do
      group "Software"

      def options(opt)
        opt.banner <<-BANNER
          Usage: latest

          Display the latest version of collins shell
        BANNER
      end

      def process
        o = CollinsShell::Console.options
        render_output CollinsShell::Cli.new([], o).get_latest_version
      end

    end # create_command

    create_command "version", "Current version of collins shell" do
      group "Software"

      def options(opt)
        opt.banner <<-BANNER
          Usage: version

          Display the current version of collins shell
        BANNER
      end

      def process
        o = CollinsShell::Console.options
        render_output CollinsShell::Cli.new([], o).get_version
      end

    end # create_command

  end # CommandSet
end; end; end
