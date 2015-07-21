require 'collins_shell/thor'
require 'collins_shell/util'
require 'collins_shell/util/asset_printer'
require 'thor'
require 'thor/group'

module CollinsShell

  class Asset < Thor
    include ThorHelper
    include CollinsShell::Util
    namespace :asset
    def self.banner task, namespace = true, subcommand = false
      "#{basename} #{task.formatted_usage(self, true, subcommand).gsub(':',' ')}"
    end

    desc 'create', 'create an asset in collins'
    use_collins_options
    use_tag_option(true)
    method_option :ipmi, :type => :boolean, :desc => 'Generate IPMI data'
    method_option :status, :type => :string, :desc => 'Status of asset'
    method_option :type, :type => :string, :desc => 'Asset type'
    def create
      call_collins get_collins_client, "create asset" do |client|
        asset = client.create! options.tag, :generate_ipmi => options.ipmi, :status => options.status, :type => options.type
        print_find_results asset, nil
      end
    end

    desc 'delete [NUKE=BOOLEAN]', 'delete an asset in collins (must not be allocated)'
    use_collins_options
    use_tag_option(true)
    use_nuke_option(true)
    method_option :reason, :required => true, :type => :string, :desc => 'Reason to delete asset'
    method_option :nuke, :required => false, :type => :boolean, :default => :false, :desc => 'Destroy asset forever?'
    def delete
      call_collins get_collins_client, "delete asset" do |client|
        if client.delete!(options.tag, :reason => options.reason, :nuke => options.nuke) then
          say_success "deleted asset #{options.tag}"
        else
          say_error "deleting asset #{options.tag}", :exit => true
        end
      end
    end

    desc 'find', 'find assets using the specified selector'
    use_collins_options
    use_selector_option(true)
    method_option :confirm, :type => :boolean, :default => :true, :desc => 'Require exec confirmation. Defaults to true'
    method_option :details, :type => :boolean, :desc => 'Output assets how you see them in get'
    method_option :exec, :type => :string, :desc => 'Execute a command using the data from this asset. Use {{hostname}}, {{ipmi.password}}, etc for substitution'
    method_option :header, :type => :boolean, :default => true, :desc => 'Display a tag header. Defaults to true'
    method_option :logs, :type => :boolean, :default => false, :desc => 'Also display asset logs, only used with details (SLOW)'
    method_option :tags, :type => :array, :desc => 'Tags to display of form: tag1 tag2 tag3. e.g. --tags=hostname tag backend_ip_address'
    method_option :threads, :type => :numeric, :default => 1, :desc => 'Number of threads to use for --exec. Defaults to 1.'
    method_option :url, :type => :boolean, :default => true, :desc => 'Display a URL along with tags or the default listing'
    def find
      client = get_collins_client
      tags = options.tags || [:stuff] 
      selector = get_selector options.selector, tags, options["size"], options.remote
      assets = client.find selector
      if options.details then
        assets.each do |asset|
          if not options.quiet then
            logs = []
            if options.logs and not options.exec? then
              logs = client.logs(asset, :size => 5000, :SORT => "DESC").reverse
            end
            printer = CollinsShell::AssetPrinter.new asset, self, :separator => '*',
                                                                  :logs => logs,
                                                                  :detailed => !options.exec?
            puts printer
          end
          asset_exec asset, options.exec, options.confirm, options.threads
        end
      else
        if not options.quiet then
          print_find_results assets, options.tags, :header => options.header, :url => options.url
        end
        assets.each {|asset| asset_exec(asset, options.exec, options.confirm, options.threads)}
      end
      finalize_exec
    end

    desc 'find_similar TAG', 'get similar unallocated assets for a gven asset'
    use_collins_options
    method_option :size, :type => :numeric, :default => 50, :desc => 'number of results to find. Defaults to 50'
    method_option :details, :type => :boolean, :desc => 'Output assets how you see them in get'
    method_option :sort, :type => :string, :default => "DESC", :desc => 'sort direction, either ASC or DESC'
    method_option :sort_type, :type => :string, :default => "distribution", :desc => 'sorting type'
    method_option :only_unallocated, :type => :boolean, :default => true, :desc => 'only return unallocated assets, defaults to true'
    def find_similar tag
      call_collins get_collins_client, "similar asset" do |client|
        as_asset = Collins::Asset.new(tag)
        assets = client.find_similar as_asset, options["size"], options["sort"], options.sort_type, options.only_unallocated
        if options.details then
          assets.each do |asset|
            printer = CollinsShell::AssetPrinter.new asset, self, :separator => '*'
            puts printer
          end
        else
          print_find_results assets, options.tags
        end
      end
    end

    desc 'get TAG', 'get an asset and display its attributes'
    use_collins_options
    method_option :confirm, :type => :boolean, :default => :true, :desc => 'Require exec confirmation. Defaults to true'
    method_option :exec, :type => :string, :desc => 'Execute a command using the data from this asset. Use {{hostname}}, {{ipmi.password}}, etc for substitution'
    method_option :logs, :type => :boolean, :default => false, :desc => 'Also display asset logs'
    method_option :remote, :type => :string, :desc => 'Remote location to search. This is a tag in collins corresponding to the datacenter asset'
    def get tag
      asset = asset_get tag, options
      if asset then
        if not options.quiet then
          logs = []
          if options.logs and not options.exec? then
            logs = call_collins(get_collins_client, "logs") do |client|
              client.logs(asset, :size => 5000, :sort => "DESC").reverse
            end
          end
          printer = CollinsShell::AssetPrinter.new asset, self, :logs => logs, :detailed => !options.exec?
          puts printer
        end
        asset_exec asset, options.exec, options.confirm
      else
        say_error "No such asset #{tag}"
      end
    end

    desc 'set_attribute KEY VALUE', 'set an attribute in collins'
    use_collins_options
    use_tag_option
    use_selector_option
    method_option :json, :type => :boolean, :default => false, :desc => 'Encode as JSON value. Only works for arrays and hashes.'
    method_option :dimension, :type => :numeric, :default => -1, :desc => 'Data dimension, must be a number'
    def set_attribute key, value
      batch_selector_operation Hash[
        :remote => options.remote,
        :operation => "set_attribute",
        :size => options["size"],
        :success_message => proc {|asset| "Set attribute on #{asset.tag}"},
        :error_message => proc{|asset| "Setting attribute on #{asset.tag}"},
        :confirmation_message => proc do |assets|
          "You are about to set #{key}=#{value} on #{assets.length} hosts. ARE YOU SURE?"
        end
      ] do |client,asset|
        if options.json then
          value = JSON.dump(eval(value))
        end
        dimension = nil
        if options.dimension >= 0 then
          dimension = options.dimension
        end
        client.set_attribute!(asset, key, value, dimension)
      end
    end

    desc 'set_attributes KEY=VALUE [KEY=VALUE,...]', 'set multiple attributes on an asset in collins'
    use_collins_options
    use_tag_option
    use_selector_option
    def set_attributes *hash
      attributes = hash.inject({}) do |ret, e|
        k, v = e.split('=', 2)
        if k.nil? or v.nil? then
          k, v = e.split(':', 2)
          if k.nil? or v.nil? then
            raise ::CollinsShell::RequirementFailedError.new "Expected key=value (or key:value) format"
          end
        end
        ret[k] = v
        ret
      end
      batch_selector_operation Hash[
        :remote => options.remote,
        :operation => "set_attributes",
        :size => options["size"],
        :success_message => proc {|asset| "Set attributes on #{asset.tag}"},
        :error_message => proc{|asset| "Setting attributes on #{asset.tag}"},
        :confirmation_message => proc do |assets|
          "You are about to set #{attributes.inspect} on #{assets.length} hosts. ARE YOU SURE?"
        end
      ] do |client,asset|
        client.set_multi_attribute!(asset, attributes)
      end
    end

    desc 'delete_attribute KEY', 'delete an attribute in collins'
    use_collins_options
    use_tag_option
    use_selector_option
    def delete_attribute key
      batch_selector_operation Hash[
        :remote => options.remote,
        :operation => "delete_attribute",
        :size => options["size"],
        :success_message => proc {|asset| "Delete attribute on #{asset.tag}"},
        :error_message => proc{|asset| "Delete attribute on #{asset.tag}"},
        :confirmation_message => proc do |assets|
          "You are about to delete #{key} on #{assets.length} hosts. ARE YOU SURE?"
        end
      ] do |client,asset|
        client.delete_attribute!(asset, key)
      end
    end

    desc 'set_status', 'set status, state, or both on an asset'
    use_collins_options
    use_tag_option
    use_selector_option
    method_option :reason, :type => :string, :required => true, :desc => 'Reason for changing status'
    method_option :state, :type => :string, :required => false, :desc => 'Set state of asset as well'
    method_option :status, :type => :string, :required => false, :desc => 'Set status of asset'
    def set_status
      status = options.status
      state = options.state
      reason = options.reason
      if status.nil? && state.nil? then
        raise ::Collins::ExpectationFailedError.new("set_status requires either a status or a state")
      end
      batch_selector_operation Hash[
        :remote => options.remote,
        :operation => "set_status",
        :size => options["size"],
        :success_message => proc {|asset| "Set status to #{status} on #{asset.tag}"},
        :error_message => proc{|asset| "Setting status on #{asset.tag}"},
        :confirmation_message => proc do |assets|
          "You are about to set status to #{status} on #{assets.length} hosts. ARE YOU SURE?"
        end
      ] do |client,asset|
        client.set_status!(asset, :reason => reason, :status => status, :state => state)
      end
    end

  end

end
