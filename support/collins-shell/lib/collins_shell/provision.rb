require 'collins_shell/thor'
require 'collins_shell/util'
require 'thor'
require 'thor/group'
require 'terminal-table'

module CollinsShell

  class Provision < Thor
    include ThorHelper
    include CollinsShell::Util

    desc 'list', 'list available provisioning profiles'
    use_collins_options
    def list
      table = Terminal::Table.new
      table.title = set_color("Provisioning Profiles", :bold, :magenta)
      table << [
        "Profile", "Label", "Prefix", "Pool", split("Primary Role"), split("Secondary Role"),
        split("Suffix Allowed?"), split("Requires Primary Role?"),
        split("Requires Secondary Role?"), split("Requires Pool?")
      ]
      table << :separator
      data = call_collins get_collins_client, '/api/provision/profiles' do |client|
        profs = client.provisioning_profiles
        last = profs.size - 1
        profs.each_with_index do |profile, idx|
          table << [
            profile.profile, split(profile.label), profile.prefix, optional_string(profile.pool),
            optional_string(profile.primary_role), optional_string(profile.secondary_role),
            centered(profile.suffix_allowed?), centered(profile.requires_primary_role?),
            centered(profile.requires_secondary_role?), centered(profile.requires_pool?)
          ]
          if idx != last && too_wide? then
            table << :separator
          end
        end
      end
      puts table
    end

    desc 'host TAG PROFILE CONTACT', 'provision host with asset tag TAG as PROFILE, and then contact CONTACT'
    long_desc <<-D
      Provision the host specified by the TAG argument as a PROFILE.

      PROFILE must be one of the profiles found by doing a `collins-shell provision list`

      CONTACT should be your hipchat username, or the username part of your email address.

      This task may take up to 90 seconds to return.
    D
    use_collins_options
    method_option :activate, :type => :boolean, :desc => 'Activate a server', :hide => true
    method_option :confirm, :type => :boolean, :default => :true, :desc => 'Require confirmation. Defaults to true'
    method_option :exec, :type => :string, :desc => 'Execute a command using the data from this asset. Use {{hostname}}, {{ipmi.password}}, etc for substitution'
    method_option :pool, :type => :string, :desc => 'Pool for host'
    method_option :primary_role, :type => :string, :desc => 'Primary role for host'
    method_option :secondary_role, :type => :string, :desc => 'Secondary role for host'
    method_option :suffix, :type => :string, :desc => 'Suffix to use for hostname'
    method_option :verify, :type => :boolean, :default => :true, :desc => 'Verify arguments locally first'
    def host tag, profile, contact
      verify_profile(profile) if options.verify
      config = get_collins_config
      if config[:timeout] < 180 then
        cclient = get_collins_client :timeout => 180
      else
        cclient = get_collins_client
      end
      asset = asset_get tag, options
      if options.confirm then
        printer = CollinsShell::AssetPrinter.new asset, self, :detailed => false
        puts printer
        require_yes "You are about to provision asset #{tag} as a #{profile}. ARE YOU SURE?", :red
      end
      progress_printer = Thread.new {
        loop {
          print "."
          sleep(1)
        }
      }
      status = call_collins cclient, "provision host" do |client|
        say_status("starting", "Provisioning has started", :white)
        client.provision tag, profile, contact, :activate => options.activate,
                                                :pool => options.pool,
                                                :primary_role => options.primary_role,
                                                :secondary_role => options.secondary_role,
                                                :suffix => options.suffix
      end
      progress_printer.terminate
      puts()
      if status then
        say_success("Successfully provisioned asset")
        asset = asset_get tag, options
        printer = CollinsShell::AssetPrinter.new asset, self, :detailed => false
        puts printer
        asset_exec asset, options.exec, options.confirm
      else
        say_error("Failed to provision asset")
      end
    end

    no_tasks do
      def too_wide?
        terminal_width < 150
      end

      def split value
        if too_wide? then
          value.split(' ').join("\n")
        else
          value
        end
      end

      def verify_value pname, name, user_specified, profile_exists, profile_requires, profile_provides
        if user_specified then
          require_that(!profile_exists, "#{name} is not user configurable for '#{pname}'")
        else
          require_that(!profile_requires || profile_provides, "#{name} is required for '#{pname}'")
        end
      end

      def verify_profile pname
        profiles = call_collins get_collins_client, '/api/provision/profiles' do |client|
          client.provisioning_profiles
        end
        profile = profiles.select{|p| p.profile.downcase == pname.to_s.downcase}.first
        require_non_empty(profile, "No such profile '#{pname}', try collins-shell provision list")
        if options.suffix? then
          require_that(profile.suffix_allowed?, "Suffix not allowed for '#{pname}'")
        end
        verify_value pname, "Pool", options.pool?, profile.pool?, profile.requires_pool?, profile.pool
        verify_value pname, "Secondary role", options.secondary_role?, profile.secondary_role?, profile.requires_secondary_role?, profile.secondary_role
        verify_value pname, "Primary role", options.primary_role?, profile.primary_role?, profile.requires_primary_role?, profile.primary_role
      end

      def centered value
        {:alignment => :center, :value => value}
      end

      def optional_string value, default = 'Configurable'
        if value.nil? || value.empty? then
          default
        else
          value
        end
      end # optional_string
    end

  end

end
