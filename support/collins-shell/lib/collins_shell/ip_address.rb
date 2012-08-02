require 'collins_shell/thor'
require 'collins_shell/util'
require 'thor'
require 'thor/group'

module CollinsShell

  class IpAddress < Thor

    include ThorHelper
    include CollinsShell::Util
    namespace :ip_address
    def self.banner task, namespace = true, subcommand = false
      "#{basename} #{task.formatted_usage(self, true, subcommand).gsub(':',' ')}"
    end

    desc 'allocate POOL', 'allocate addresses for an asset in the specified pool'
    use_collins_options
    use_tag_option(true)
    method_option :count, :type => :numeric, :default => 1, :desc => 'Number of addresses to allocate'
    def allocate pool
      call_collins get_collins_client, "ip_address allocate" do |client|
        addresses = client.ipaddress_allocate! options.tag, pool, options["count"]
        header = [["Gateway","Netmask","Address","Pool"]]
        tags = header + addresses.map do |address|
          [address.gateway, address.netmask, address.address, address.pool]
        end
        print_table tags
      end
    end

    desc 'delete POOL', 'delete addresses for an asset in the specified pool'
    use_collins_options
    use_tag_option(true)
    def delete pool
      call_collins get_collins_client, "ip_address delete" do |client|
        delete_count = client.ipaddress_delete! options.tag, pool
        say_success "Deleted #{delete_count} addresses"
      end
    end

    desc 'delete_all', 'delete all addresses for an asset'
    use_collins_options
    use_tag_option(true)
    def delete_all
      call_collins get_collins_client, "ip_address delete_all" do |client|
        delete_count = client.ipaddress_delete! options.tag
        say_success "Deleted #{delete_count} addresses"
      end
    end

    desc 'find ADDRESS', 'find asset using specified IP address'
    use_collins_options
    def find address
      call_collins get_collins_client, "ip_address find" do |client|
        asset = client.asset_at_address address
        if asset then
          say_success "Asset #{asset.tag} is using address #{address}"
        else
          say_error "No asset using address #{address}"
        end
      end
    end

    desc 'assets POOL', 'find all assets in a pool'
    use_collins_options
    method_option :details, :type => :boolean, :default => false, :desc => 'Retrieve details for each asset. SLOW'
    def assets pool
      call_collins get_collins_client, "ip_address assets" do |client|
        header = true
        client.assets_in_pool(pool).each do |asset|
          if options.details then
            asset = client.get asset
            print_find_results asset, [:tag,:status,:type,:hostname,:addresses], :header => header
            header = false
          else
            puts(asset.tag)
          end
        end
      end
    end

    desc 'pools', 'find all pools that are in use'
    use_collins_options
    method_option :used, :type => :boolean, :default => false, :desc => 'Only pools that are in use'
    def pools
      call_collins get_collins_client, "ip_address pools" do |client|
        # NAME, NETWORK, START_ADDRESS, SPECIFIED_GATEWAY, GATEWAY, BROADCAST, POSSIBLE_ADDRESSES
        header = [["Pool","Network","Gateway","Broadcast","Possible Addresses","Start Address","Specified Gateway"]]
        rows = client.ipaddress_pools(!options.used).map do |pool|
          [pool["NAME"], pool["NETWORK"], pool["GATEWAY"], pool["BROADCAST"], pool["POSSIBLE_ADDRESSES"],
            pool["START_ADDRESS"], pool["SPECIFIED_GATEWAY"]]
        end
        print_table header + rows
      end
    end

    desc 'create', 'create a new IP address (Use allocate, not create)'
    use_collins_options
    use_tag_option(true)
    method_option :address, :required => true, :type => :string, :desc => 'IP address'
    method_option :gateway, :required => true, :type => :string, :desc => 'IP gateway'
    method_option :netmask, :required => true, :type => :string, :desc => 'IP netmask'
    method_option :pool, :type => :string, :desc => 'Name of pool'
    def create
      call_collins get_collins_client, "create address" do |client|
        address = client.ipaddress_update! options.tag, nil,
                                                :address => options.address,
                                                :gateway => options.gateway,
                                                :netmask => options.netmask,
                                                :pool => options.pool
        if address then
          say_success "Address for #{options.tag} created"
        else
          say_error "Address for #{options.tag} not created"
        end
      end
    end

    desc 'update OLD_ADDRESS', 'update the IP info for an asset'
    use_collins_options
    use_tag_option(true)
    method_option :address, :type => :string, :desc => 'New IP address'
    method_option :gateway, :type => :string, :desc => 'New IP gateway'
    method_option :netmask, :type => :string, :desc => 'New IP netmask'
    method_option :pool, :type => :string, :desc => 'New pool'
    def update old_address
      call_collins get_collins_client, "update address" do |client|
        address = client.ipaddress_update! options.tag, old_address,
                                                :address => options.address,
                                                :gateway => options.gateway,
                                                :netmask => options.netmask,
                                                :pool => options.pool
        if address then
          say_success "Address for #{options.tag} updated"
        else
          say_error "Address for #{options.tag} not updated"
        end
      end
    end

  end

end
