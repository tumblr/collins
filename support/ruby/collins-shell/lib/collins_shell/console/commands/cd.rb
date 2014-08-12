require 'pry'

require 'collins_shell/console/filesystem'

module CollinsShell; module Console; module Commands
  Cd = Pry::CommandSet.new do
    create_command "cd" do
      include CollinsShell::Console::CommandHelpers

      description "Change context to an asset or path"
      group "Context"

      banner <<-BANNER
        Usage: cd /<tag>
               cd <tag>
               cd /path
               cd path

        Changes context to the specified tag or path.
        Further commands apply to the asset or path.
      BANNER
      def process
        path = arg_string.split(/\//)
        stack  = _pry_.binding_stack.dup

        stack = [stack.first] if path.empty?

        path.each do |context|
          begin
            case context.chomp
            when ""
              stack = [stack.first]
            when "."
              next
            when ".."
              unless stack.size == 1
                stack.pop
              end
            else
              # We know we start out with the root fs being the context
              fs_parent = stack.last.eval('self')
              # Pushing the path value onto the parent gives us back a child
              begin
                fs_child = fs_parent.push(context)
                # We can't have assets as children of assets, replace current asset with new one
                output.puts fs_child.path
                stack.push(Pry.binding_for(fs_child))
              rescue Exception => e
                output.puts("#{text.bold('Could not change context:')} #{e}")
              end
            end
          rescue Exception => e
            output.puts e.backtrace
            output.puts("Got exception: #{e}")
          end 
        end # path.each
        _pry_.binding_stack = stack
      end # def process
    end
  end
end; end; end
