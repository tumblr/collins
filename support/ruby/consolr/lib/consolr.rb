$LOAD_PATH.unshift('./lib/')

require 'consolr/version'
require 'collins_auth'
require 'optparse'
require 'yaml'


module Consolr
  class Console

    attr_reader :ipmitool_exec, :dangerous_assets, :dangerous_status, :dangerous_actions, :options, :opt_parser

    def initialize
      potential_config_paths = [ENV['CONSOLR_CONFIG'],
                                '~/.consolr.yml', '~/.consolr.yaml',
                                '/etc/consolr.yml', '/etc/consolr.yaml',
                                '/var/db/consolr.yml', '/var/db/consolr.yaml']
      config_file = potential_config_paths.compact.find do |conf|
        begin
          expanded_path = File.expand_path(conf, __FILE__)
          File.readable?(expanded_path) and File.size(expanded_path) > 0
        rescue ArgumentError
          # if $HOME is not set, or if `~` cannot be expanded, `expand_path` will throw and ArgumentError
          # in that case, just go to the next potential config file - this one obviously will not work
          false
        end
      end

      @config_params = begin
        YAML.load(File.open(File.expand_path(config_file, __FILE__)))
      rescue TypeError => e
        puts "-------"
        puts "Failed to load Configuration File ... "
        puts "Looks like a configuration file doesn't exist."
        puts "Please look at README.md on creating a configuration file"
        puts "-------"
        exit 1
      rescue ArgumentError => e
        puts "------"
        puts "Failed to load Configuration File ... "
        puts "Looks like the configuration file is not correctly formatted"
        puts "Please check if your file conforms to YAML spec"
        puts "------"
        exit 1
      end
      #
      # Will be ignored for dangerous actions, no matter what, even with --force
      begin
        @dangerous_assets = @config_params['dangerous_assets']
      rescue Exception => e
        puts e
        puts "-------"
        puts "Dangerous Assets -- #{dangrous}"
        puts "Please make sure dangerous_assets exists and is valid."
        puts "Supply them in a comma separated list."
      end

      # Dangerous actions wont be run in these status, override with --force
      begin
        @dangerous_status = @config_params['dangerous_status']
      rescue Exception => e
        puts e
        puts "-------"
        puts "Dangerous Status -- #{@dangeous_status}"
        puts "Please specify the statuses which are dangerorous, during which dangerous shouldn't be run."
        puts "-------"
        exit 1
      end

      @dangerous_actions = [:off, :reboot] # Must match the symbol in options{}
    end

    def start options
      abort("Please pass either the asset tag or hostname") if options[:tag].nil? and options[:hostname].nil?

      dangerous_body = "Dangerous actions: #{dangerous_actions.join(', ')}\n"\
        "Dangerous status: #{dangerous_status.join(', ')} (override with --force)\n"\
        "Dangerous assets: #{dangerous_assets.join(', ')} (ignored no matter what, even with --force)"

      if options[:dangerous]
        puts dangerous_body
        exit 1
      end

      begin
        collins = Collins::Authenticator.setup_client
      rescue Exception => e
        puts e
        puts "-------"
        puts "There was a problem setting up a connection with Collins."
        puts "-------"
        exit 1
      end

      if options[:tag] and options[:hostname]
        abort("Please pass either the hostname OR the tag but not both.")
      end

      # match assets like vm-67f5eh, zt-*, etc.
      nodes = options[:tag] ? (collins.find :tag => options[:tag]) : (collins.find :hostname => options[:hostname])
      @node = nodes.length == 1 ? nodes.first : abort("Found #{nodes.length} assets, aborting.")

      selected_dangerous_actions = dangerous_actions.select { |o| options[o] }
      if dangerous_assets.include?(@node.tag) and selected_dangerous_actions.any?
        abort "Asset #{@node.tag} is a crucial asset. Can't ever execute dangerous actions on this asset.\n#{dangerous_body}"
      end

      if options[:force].nil? and selected_dangerous_actions.any? and dangerous_status.include?(@node.status)
        abort "Cannot run dangerous commands on #{@node.hostname} (#{@node.tag} - #{@node.status}) because it is in a protected status. This can be overridden with the --force flag\n#{dangerous_body}"
      end

      # use the command line runner, if it was provided
      runner_names = [options[:runner]].compact

      # if no runner specified on command line, use the runners from the config
      if runner_names.empty?
        runner_names = @config_params.fetch('runners', []).compact
      end

      # if neither of the above is true, default to using ipmitool
      if runner_names.empty?
        runner_names = ['ipmitool']
      end

      # select the first runner that support the node
      runner_names.each do |runner_name|

        # load the runner
        begin
          require "consolr/runners/#{runner_name}"
          runner = Consolr::Runners.const_get(runner_name.capitalize).new @config_params.fetch(runner_name, {})
        rescue NameError, LoadError => e
          puts "Could not load runner #{runner_name.capitalize}, skipping."
          next
        end

        # if this runner can't work for this node, try the next runner
        if not runner.can_run? @node
          next
        end

        if not runner.verify @node
          abort("Cannot verify asset #{@node.hostname} (#{@node.tag})")
        end

        # run the command!
        case
        when options[:console]
          puts '--> Opening SOL session (type ~~. to quit)'
          puts runner.console @node
        when options[:kick]
          puts runner.kick @node
        when options[:identify]
          puts runner.identify @node
        when options[:sdr]
          puts runner.sdr @node
        when options[:log] == 'list'
          puts runner.log_list @node
        when options[:log] == 'clear'
          puts runner.log_clear @node
        when options[:on]
          puts runner.on @node
        when options[:off]
          puts runner.off @node
        when options[:soft_off]
          puts runner.soft_off @node
        when options[:reboot]
          puts runner.reboot @node
        when options[:soft_reboot]
          puts runner.soft_reboot @node
        when options[:status]
          puts runner.status @node
        when options[:sensors]
          puts runner.sensors @node
        when options[:get_sol_info]
          puts runner.sol_info @node
        else
          begin
            abort("specify an action")
          end
        end
        # everything worked!
        exit 0
      end

      # if we got here, all the runners did not work for this node
      abort("No runners available for node #{@node.hostname} (#{@node.tag})")
    end

  end
end
