require 'terminal-table'
require 'pry'

require 'collins_shell/console/commands'
require 'collins_shell/console/filesystem'

class Pry
  # We kill the built in completion so we can tell users what things are available
  module InputCompleter
    class << self
      def build_completion_proc(target, commands=[""], cmplns = nil)
        if cmplns.is_a?(Array) then # Work around for breakage between pry 0.9.9.6 and 0.9.12
          commands = cmplns
        end
        proc do |input|
          commands.map do |cmd|
            cmd_s = cmd.to_s
            if cmd_s.include?(":wtf") then
              "wtf?"
            else
              cmd_s
            end
          end.select{|s| s.start_with?(input)}.uniq # commands.map
        end # proc do
      end # def build_completion_proc
    end # class << self
  end # module InputCompleter
end # class Pry

module CollinsShell; module Console

  class << self
    def run_pry_command command_string, options = {}
      options = {
        :show_output => true,
        :output => Pry.output,
        :commands => get_pry_commands
      }.merge!(options)
      output = options[:show_output] ? options[:output] : StringIO.new
      pry = Pry.new(
              :output => output, :input => StringIO.new(command_string),
              :commands => options[:commands], :prompt => proc{""}, :hooks => Pry::Hooks.new
      )
      if options[:binding_stack] then
        pry.binding_stack = options[:binding_stack]
      end
      pry.rep(options[:context])
    end
    def launch(options)
      self.options = options
      Pry.config.commands = get_pry_commands
      Pry.config.pager = true
      Pry.custom_completions = get_pry_custom_completions
      Pry.config.exception_handler = get_pry_exception_handler
      target = CollinsShell::Console::Filesystem.new options
      setup_pry_hooks
      Pry.start(target, :prompt => get_pry_prompt)
    end
    def options=(options)
      @options = options
    end
    def options
      @options
    end
    private
    def setup_pry_hooks
      before_message = [
        'Welcome to the collins console. A few notes:',
        ' - collins-shell interacts with real servers (BE CAREFUL).',
        ' - collins-shell operates in contexts, which the prompt tells you about.',
        ' - collins-shell can be customized to your tastes. Read the docs.',
        ' - Type help at any time for help'
      ].join("\n")
      Pry.config.hooks.add_hook(:before_session, :session_start) do |out, *|
        out.puts before_message
      end
      Pry.config.hooks.add_hook(:after_session, :session_end) do |out, *|
        out.puts "Goodbye!"
      end
    end
    def get_pry_prompt
      get_prompt = proc { |o, waiting|
        sym = waiting ? "*" : ">"
        if o.is_a?(CollinsShell::Console::Filesystem) then
          ext = ""
          if o.asset? then
            ext = "*"
          end
          "collins #{o.path}#{ext} #{sym} "
        else
          "collins-#{o.class} #{sym} "
        end
      }
      [
        proc { |o, *| get_prompt.call(o, false) },
        proc { |o, *| get_prompt.call(o, true) }
      ]
    end
    def get_pry_custom_completions
      proc do
        last = binding_stack.last
        last = last.eval('self') unless last.nil?
        if last.is_a?(CollinsShell::Console::Filesystem) then
          (last.available_commands + commands.commands.keys).flatten
        else
          commands.commands.keys
        end
      end
    end
    def get_pry_exception_handler
      proc do |output, exception, _pry_|
        if exception.is_a?(Interrupt) then
          output.puts ""
        else
          cli = CollinsShell::Cli.new [], {}
          cli.print_error exception, "command failed"
          output.puts ""
          bold = Pry::Helpers::Text.bold("type 'bt' or 'wtf?!?' for more context")
          output.puts bold
        end
      end
    end
    def get_pry_commands
      Pry::CommandSet.new do
        import_from Pry::Commands, "help", "history", "hist", "wtf?", "show-doc", "show-source"
        alias_command "bt", "wtf?"
        import CollinsShell::Console::Commands::Default
      end
    end
  end

end; end
