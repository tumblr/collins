$LOAD_PATH.unshift('./lib/')

require 'collins_auth'
require 'optparse'
require 'yaml'

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

      config_params = begin
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

      begin
        @ipmitool_exec = config_params['ipmitool'] # ipmitool absolute path
      rescue Exception => e
        puts e
        puts "-------"
        puts "Ensure that the ipmitool's path (#{@ipmitool_exec}) is given in the consolr.yml file and is correct"
        puts "-------"
        exit 1
      end

      # Will be ignored for dangerous actions, no matter what, even with --force
      begin
        @dangerous_assets = config_params['dangerous_assets']
      rescue Exception => e
        puts e
        puts "-------"
        puts "Dangerous Assets -- #{dangrous}"
        puts "Please make sure dangerous_assets exists and is valid."
        puts "Supply them in a comma separated list."
        puts "-------"
        exit 1
      end

      # Dangerous actions wont be run in these status, override with --force
      begin
        @dangerous_status = config_params['dangerous_status']
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

    def ipmitool_cmd action
      system("#{@ipmitool_exec} -I lanplus -H #{@node.ipmi.address} -U #{@node.ipmi.username} -P #{@node.ipmi.password} #{action}")
      return $?.exitstatus == 0 ? "SUCCESS" : "FAILED"
    end

    def start options
      abort("Please pass either the asset tag or hostname") if options[:tag].nil? and options[:hostname].nil?

      abort("Cannot find #{@ipmitool_exec}") unless File.exist?(@ipmitool_exec)

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

      %x(/bin/ping -c 1 #{@node.ipmi.address})
      abort("Cannot ping IP #{@node.ipmi.address} (#{@node.tag})") unless $?.exitstatus == 0

      if dangerous_assets.include?(@node.tag) and dangerous_actions.any?
        puts "Asset #{@node.tag} is a crucial asset. Can't execute dangerous actions on this asset."
      end

      if options[:force].nil? and dangerous_actions.any? and dangerous_status.include?(@node.status)
        puts "Cannot run dangerous commands on #{@node.hostname} (#{@node.tag} - #{@node.status})"
        abort dangerous_body
      end

      case
      when options[:console]
        puts '--> Opening SOL session (type ~~. to quit)'
        puts ipmitool_cmd('sol activate')
      when options[:kick]
        puts ipmitool_cmd('sol deactivate')
      when options[:identify]
        puts ipmitool_cmd('chassis identify')
      when options[:sdr]
        puts ipmitool_cmd('sdr elist all')
      when options[:log] == 'list'
        puts ipmitool_cmd('sel list')
      when options[:log] == 'clear'
        puts ipmitool_cmd('sel clear')
      when options[:on]
        puts ipmitool_cmd('power on')
      when options[:off]
        puts ipmitool_cmd('power off')
      when options[:reboot]
        puts ipmitool_cmd('power cycle')
      else
        begin
          raise OptionParser::MissingArgument, "specify an action"
        rescue OptionParser::MissingArgument => e
          puts e
          puts @opt_parser
          exit 0
        end
      end
    end

  end

end
