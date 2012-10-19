require 'collins_shell/asset'
require 'collins_shell/console'
require 'collins_shell/ip_address'
require 'collins_shell/ipmi'
require 'collins_shell/provision'
require 'collins_shell/state'
require 'collins_shell/tag'
require 'collins_shell/thor'
require 'collins_shell/util'
require 'collins_shell/util/log_printer'
require 'thor'
require 'thor/group'

# Need these to support latest method
require 'rubygems'
require 'rubygems/spec_fetcher'

module CollinsShell

  class Cli < Thor

    include ThorHelper
    include CollinsShell::Util

    register(CollinsShell::Asset, 'asset', 'asset <command>', 'Asset related commands')
    register(CollinsShell::Tag, 'tag', 'tag <command>', 'Tag related commands')
    register(CollinsShell::IpAddress, 'ip_address', 'ip_address <command>', 'IP address related commands')
    register(CollinsShell::Ipmi, 'ipmi', 'ipmi <command>', 'IPMI related commands')
    register(CollinsShell::Provision, 'provision', 'provision <command>', 'Provisioning related commands')
    register(CollinsShell::State, 'state', 'state <command>', 'State management related commands - use with care')

    desc 'latest', 'check if there is a newer version of collins-shell'
    def latest
      puts(get_latest_version)
    end

    desc 'version', 'current version of collins-shell'
    def version
      puts("collins-shell #{get_version}")
    end

    desc 'power_status', 'check power status on an asset'
    use_collins_options
    use_tag_option
    use_selector_option
    def power_status
      batch_selector_operation Hash[
        :remote => options.remote,
        :operation => "power_status",
        :success_message => proc {|asset| "Got power status for #{asset.tag}"},
        :error_message => proc{|asset| "Error getting power status for #{asset.tag}"},
        :confirmation_message => proc do |assets|
          "You are about to check the power status of #{assets.length} hosts. ARE YOU SURE?"
        end
      ] do |client,asset|
        puts('*'*80)
        begin
          status = client.power_status(asset)
          say_success "Power status of #{asset.tag}: #{status}"
        rescue Exception => e
          print_error e, "Unable to check power status of #{asset.tag}", false
        end
      end
    end

    desc 'power ACTION', 'perform power action (off, on, rebootSoft, rebootHard, etc) on an asset'
    use_collins_options
    use_tag_option(true)
    method_option :reason, :type => :string, :required => true, :desc => 'Reason for reboot'
    def power action
      action = Collins::Power.normalize_action(action)
      call_collins get_collins_client, "power" do |client|
        client.log! options.tag, options.reason, 'ALERT'
        if client.power!(options.tag, action) then
          say_success "power #{action} on #{options.tag}"
        else
          say_error "power #{action} on #{options.tag}", :exit => true
        end
      end
    end

    desc 'log MESSAGE', 'log a message on an asset'
    use_collins_options
    use_tag_option
    use_selector_option
    method_option :severity, :type => :string, :desc => 'Log severity (EMERGENCY, WARN, etc)'
    def log message
      batch_selector_operation Hash[
        :remote => options.remote,
        :operation => "log",
        :success_message => proc {|asset| "Logged to #{asset.tag}"},
        :error_message => proc{|asset| "Log to #{asset.tag}"},
        :confirmation_message => proc do |assets|
          "You are about to log '#{message}' on #{assets.length} hosts. ARE YOU SURE?"
        end
      ] do |client,asset|
        client.log!(asset, message, options.severity)
      end
    end

    desc 'logs TAG', 'fetch logs for an asset specified by its tag. Use "all" for all logs'
    use_collins_options
    use_page_options(5000)
    method_option :severity, :type => :array, :desc => 'Severities to include'
    def logs tag
      call_collins get_collins_client, "logs" do |client|
        severity = []
        if options.severity? then
          severity = options.severity
        end
        params = Hash[
          :filter => severity.join(';'),
          :size => options[:size].to_i,
          :sort => options[:sort]
        ]
        logs = client.logs tag, params.merge(:all_tag => 'all')
        logs.reverse! if options[:sort].to_s.downcase == 'desc'
        printer = CollinsShell::LogPrinter.new(tag, logs)
        puts printer.render
      end
    end

    desc 'search_logs QUERY', 'search for asset logs'
    use_collins_options
    use_page_options(5000)
    def search_logs query
      call_collins get_collins_client, "logs" do |client|
        params = Hash[
          :query => query,
          :size => options[:size].to_i,
          :sort => options[:sort],
        ]
        logs = client.search_logs params
        printer = CollinsShell::LogPrinter.new("all assets", logs)
        puts printer.render
      end
    end

    desc 'console', 'drop into the interactive collins shell'
    use_collins_options
    def console
      ensure_password
      CollinsShell::Console.launch(options)
    end

    no_tasks do
      def get_version
        version_file = File.absolute_path(File.join(File.dirname(__FILE__), '..', '..', 'VERSION'))
        if File.exists?(version_file) then
          File.read(version_file)
        else
          "0.0.0"
        end
      end

      def get_latest_version
        begin
          Gem.sources = ["http://repo.tumblr.net:9929"]
          gem = Gem::SpecFetcher.new
          shell = gem.fetch(Gem::Dependency.new('collins_shell')).flatten.first
        rescue Exception => e
          return "Could not retrieve latest gem info from #{Gem.sources.join(',')}"
        end
        if shell.nil? then
          "Could not retrieve latest gem info from repo.tumblr.net"
        else
          my_version = Gem::Version.new(get_version)
          if shell.version == my_version then
            "You are running the most recent version of collins-shell: #{my_version.to_s}"
          elsif shell.version > my_version then
            "Time to upgrade! You are running collins-shell #{my_version.to_s}, latest is #{shell.version.to_s}"
          else
            "You are probably a developer. You are running version #{my_version.to_s}, latest published is #{shell.version.to_s}"
          end
        end
      end

      def print_error e, cmd = nil, separator = true
        cmd = "unable to run command '#{$0} #{ARGV.join(' ')}'" unless cmd
        say_status("fatal", cmd, :red)
        say_status("exception", "#{e.message}", :red)
        is_debug = ARGV.include?("--debug") || ARGV.include?("--trace")
        if e.is_a?(Collins::RequestError) then
          puts('*'*80) if separator
          say_status("details", e.description(is_debug).strip.sub(e.message,""), :red)
        end
        if ARGV.include?("--debug") || ARGV.include?("--trace") then
          say_status("DEBUG", "local backtrace is presented latest to earliest (reverse cronological)")
          puts("#{e.class.to_s}")
          e.backtrace.each do |line|
            file, line, method = line.split(":", 3)
            puts "\t\t#{method}(#{file}:#{line})"
          end
        end
      end # print_error
    end # no_tasks


  end

end
