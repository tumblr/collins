require 'collins_shell/thor'
require 'collins_shell/util'
require 'thor'
require 'thor/group'

module CollinsShell

  class AssetType < Thor
    include ThorHelper
    include CollinsShell::Util
    namespace :asset_type

    def self.banner task, namespace = true, subcommand = false
      "#{basename} #{task.formatted_usage(self, true, subcommand).gsub(':',' ')}"
    end

    desc 'create', 'Create a new asset type'
    use_collins_options
    method_option :name, :type => :string, :required => true, :desc => 'Name of asset type. Must be all caps and unique. Can contain numbers, letters and underscores.'
    method_option :label, :type => :string, :required => true, :desc => 'A friendly (short) description of the asset type to use in visual labels. Usually just a camel case version of the name, possibly with spaces.'
    def create
      name = options.name.upcase
      label = options.label
      call_collins get_collins_client, "asset_type_create!" do |client|
        if client.asset_type_create!(name, label) then
          say_success "Successfully created asset type '#{name}'"
        else
          say_error "Failed creating asset type '#{name}'"
        end
      end
    end

    desc 'delete ATYPE', 'Delete an asset type'
    use_collins_options
    def delete atype
      call_collins get_collins_client, "asset_type_delete!" do |client|
        if client.asset_type_delete!(atype) then
          say_success "Successfully deleted asset type '#{atype}'"
        else
          say_error "Failed deleting asset type '#{atype}'"
        end
      end
    end

    desc 'get ATYPE', 'Get an asset type by name'
    use_collins_options
    def get atype
      call_collins get_collins_client, "asset_type_get" do |client|
        header = [["Name", "Label"]]
        atype = client.asset_type_get(atype)
        table = header + [[atype.name, atype.label]]
        print_table table
      end
    end

    desc 'list', 'list asset types that are configured'
    use_collins_options
    def list
      call_collins get_collins_client, "asset_type_get_all" do |client|
        header = [["Name", "Label"]]
        atypes = header + client.asset_type_get_all.map do |atype|
          [atype.name, atype.label]
        end
        print_table atypes
      end
    end

    desc 'update ATYPE', 'Update the name and/or label of an asset type'
    use_collins_options
    method_option :name, :type => :string, :required => false, :desc => 'New name of asset type. Must be all caps and unique. Can contains numbers, letters, and underscores.'
    method_option :label, :type => :string, :required => false, :desc => 'A friendly (short) description of the asset type to use in visual labels. Usually just a camel case version of the name, possibly with spaces.'
    def update atype
      name = options.name.upcase if options.name?
      label = options.label if options.label?
      call_collins get_collins_client, "asset_type_update!" do |client|
        opts = { :name => name, :label => label }
        if client.asset_type_update!(atype, opts) then
          say_success "Successfully updated asset type '#{atype}'"
        else
          say_error "Failed creating asset type '#{atype}'"
        end
      end
    end

  end

end
