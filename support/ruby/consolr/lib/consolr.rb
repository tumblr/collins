$LOAD_PATH.unshift('./lib/')

require 'consolr/version'
require 'collins_auth'
require 'optparse'
require 'yaml'

require 'consolr/runners'

module Consolr
  class Console

    attr_reader :ipmitool_exec, :dangerous_assets, :dangerous_status, :dangerous_actions, :options, :opt_parser

    def initialize
      config_file = [ENV['CONSOLR_CONFIG'],
                      '~/.consolr.yml', '~/.consolr.yaml',
                      '/etc/consolr.yml', '/etc/consolr.yaml',
                      '/var/db/consolr.yml', '/var/db/consolr.yaml'].compact.find do |conf|
        File.readable?(File.expand_path(conf, __FILE__)) and File.size(File.expand_path(conf, __FILE__)) > 0
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

      runner = Consolr::Runners::Ipmitool.new @config_params['ipmitool']

      if not runner.verify? @node
        abort("Cannot verify asset #{@node.hostname} (#{@node.tag})")
      end

      selected_dangerous_actions = dangerous_actions.select { |o| options[o] }
      if dangerous_assets.include?(@node.tag) and selected_dangerous_actions.any?
        abort "Asset #{@node.tag} is a crucial asset. Can't ever execute dangerous actions on this asset.\n#{dangerous_body}"
      end

      if options[:force].nil? and selected_dangerous_actions.any? and dangerous_status.include?(@node.status)
        abort "Cannot run dangerous commands on #{@node.hostname} (#{@node.tag} - #{@node.status}) because it is in a protected status. This can be overridden with the --force flag\n#{dangerous_body}"
      end

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
      when options[:reboot]
        puts runner.reboot @node
      else
        begin
          puts "specify an action"
          exit 1
        end
      end
    end
  end
end
