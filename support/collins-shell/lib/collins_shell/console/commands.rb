require 'pry'

require 'collins_shell/console/options_helpers'
require 'collins_shell/console/command_helpers'
require 'collins_shell/console/commands/cd'
require 'collins_shell/console/commands/cat'
require 'collins_shell/console/commands/tail'
require 'collins_shell/console/commands/io'
require 'collins_shell/console/commands/iterators'
require 'collins_shell/console/commands/versions'

# TODO List
# * Confirmation for destructive options
# * Docs
# * Remote
# * Testing
# * Ssh
# * Ping
module CollinsShell; module Console; module Commands
  Default = Pry::CommandSet.new do
    import CollinsShell::Console::Commands::Cd
    import CollinsShell::Console::Commands::Cat
    import CollinsShell::Console::Commands::Tail
    import CollinsShell::Console::Commands::Io
    import CollinsShell::Console::Commands::Iterators
    import CollinsShell::Console::Commands::Versions
  end
end; end; end
