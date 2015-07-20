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
                      '/var/db/consolr.yml', '/var/db/consolr.yaml'].compact.find do |config_file|
        File.readable? config_file and File.size(config_file) > 0
      end

      config_params = begin
        YAML.load(File.open(config_file))
      rescue TypeError => e
        puts "-------"
        puts "Failed to load Configuration File ... "
        puts "Looks like a configuration file doesn't exist."
        puts "Please look at README.md on creating a configuration file"
        puts "-------"
        exit 0
      rescue ArgumentError => e
        puts "------"
        puts "Failed to load Configuration File ... "
        puts "Looks like the configuration file is not correctly formatted"
        puts "Please check if your file conforms to YAML spec"
        puts "------"
        exit 0
      end
      
      begin
        @ipmitool_exec = config_params['ipmitool'] # ipmitool absolute path
      rescue Exception => e
        puts e
        puts "-------"
        puts "Ensure that the ipmitool's path (#{@ipmitool_exec}) is given in the consolr.yml file and is correct"
        puts "-------"
        exit 0
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
        exit 0
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
        exit 0
      end

      @dangerous_actions = [:off, :reboot] # Must match the symbol in options{}

      @options = {}
      @opt_parser = OptionParser.new do |opt|
        opt.banner = "Usage: consolr [OPTIONS]"
        opt.separator  ""
        opt.separator  "Options"
        
        opt.on("-t","--tag ASSET","asset tag") { |tag| options[:tag] = tag }
        opt.on("-H","--hostname ASSET","asset hostname") { |hostname| options[:hostname] = hostname }
        opt.on("-o","--on","turn on node") { options[:on] = true }
        opt.on("-x","--off","turn off node") { options[:off] = true }
        opt.on("-r","--reboot","restart node") { options[:reboot] = true }
        opt.on("-i","--identify","turn on chassis UID") { options[:identify] = true }
        opt.on("-c","--console","console into node via SOL") { options[:console] = true }
        opt.on("-k","--kick","kick if someone is hogging the console") { options[:kick] = true }
        opt.on("-d","--dangerous","list dangerous stuff") { options[:dangerous] = true }
        opt.on("-f","--force","force run dangerous actions") { options[:force] = true }
        opt.on("-s","--sdr","Sensor Data Repository (SDR)") { options[:sdr] = true }
        opt.on("-l","--log LOG","System Event Log (SEL) [list|clear]") { |log| options[:log] = log }
        
        opt.on("-h","--help","help") { exit 0 }
      end
    end
    
    def ipmitool_cmd action
      system("#{@ipmitool_exec} -I lanplus -H #{@node.ipmi.address} -U #{@node.ipmi.username} -P #{@node.ipmi.password} #{action}")
      return $?.exitstatus == 0 ? "SUCCESS" : "FAILED"
    end

    def start
      begin
        @opt_parser.parse! # extract from ARGV[]
        raise OptionParser::MissingArgument, "asset tag or asset hostname required" if options[:tag].nil? and options[:hostname].nil?
      rescue Exception => e
        puts @opt_parser
        exit 0
      end
      
      abort("Cannot find #{@ipmitool_exec}") unless File.exist?(@ipmitool_exec)

      dangerous_body = "Dangerous actions: #{dangerous_actions.join(', ')}\n"\
        "Dangerous status: #{dangerous_status.join(', ')} (override with --force)\n"\
        "Dangerous assets: #{dangerous_assets.join(', ')} (ignored no matter what, even with --force)"
      
      if options[:dangerous]
        puts dangerous_body
        exit 0
      end
      
      begin
        collins = Collins::Authenticator.setup_client
      rescue Exception => e
        puts e
        puts "-------"
        puts "There was a problem setting up a connection with Collins."
        puts "-------"
        exit 0
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
        puts "--> Opening SOL session (type ~~. to quit)"
        puts self.ipmitool_cmd("sol activate")
      
      when options[:kick]
        puts self.ipmitool_cmd("sol deactivate")
      
      when options[:identify]
        puts self.ipmitool_cmd("chassis identify")
      
      when options[:sdr]
        puts self.ipmitool_cmd("sdr elist all")
      
      when options[:log] == "list"
        puts self.ipmitool_cmd("sel list")
      
      when options[:log] == "clear"
        puts self.ipmitool_cmd("sel clear")
      
      when options[:on]
        puts self.ipmitool_cmd("power on")
      
      when options[:off]
        puts self.ipmitool_cmd("power off")
      
      when options[:reboot]
        puts self.ipmitool_cmd("power cycle")
      
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
