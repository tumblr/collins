require 'collins_shell/thor'
require 'collins_shell/util'
require 'thor'
require 'thor/group'

module CollinsShell

  class State < Thor
    include ThorHelper
    include CollinsShell::Util
    namespace :state

    def self.banner task, namespace = true, subcommand = false
      "#{basename} #{task.formatted_usage(self, true, subcommand).gsub(':',' ')}"
    end

    desc 'create', 'Create a new state'
    use_collins_options
    method_option :name, :type => :string, :required => true, :desc => 'Name of state. Must be all caps and unique. Can contain numbers, letters and underscores.'
    method_option :label, :type => :string, :required => true, :desc => 'A friendly (short) description of the state to use in visual labels. Usually just a camel case version of the name, possibly with spaces.'
    method_option :description, :type => :string, :required => true, :desc => 'A longer description of the state'
    method_option :status, :type => :string, :required => false, :desc => 'Optional status to bind the state to'
    def create
      name = options.name.upcase
      label = options.label
      desc = options.description
      status = options.status
      call_collins get_collins_client, "state_create!" do |client|
        if client.state_create!(name, label, desc, status) then
          say_success "Successfully created state '#{name}'"
        else
          say_error "Failed creating state '#{name}'"
        end
      end
    end

    desc 'delete STATE', 'Delete a state'
    use_collins_options
    def delete state
      call_collins get_collins_client, "state_delete!" do |client|
        if client.state_delete!(state) then
          say_success "Successfully deleted state '#{state}'"
        else
          say_error "Failed deleting state '#{state}'"
        end
      end
    end

    desc 'get STATE', 'Get a state by name'
    use_collins_options
    def get state
      call_collins get_collins_client, "state_get" do |client|
        header = [["Name", "Label", "Status", "Description"]]
        state = client.state_get(state)
        table = header + [[state.name, state.label, (state.status.name || ""), state.description]]
        print_table table
      end
    end

    desc 'list', 'list states that are available'
    use_collins_options
    def list
      call_collins get_collins_client, "state_get_all" do |client|
        header = [["Name", "Label", "Status", "Description"]]
        states = header + client.state_get_all.map do |state|
          [state.name, state.label, (state.status.name || ""), state.description]
        end
        print_table states
      end
    end

    desc 'update STATE', 'Update the name, label, description or status binding of a state'
    use_collins_options
    method_option :name, :type => :string, :required => false, :desc => 'New name of state. Must be all caps and unique. Can contains numbers, letters, and underscores.'
    method_option :label, :type => :string, :required => false, :desc => 'A friendly (short) description of the state to use in visual labels. Usually just a camel case version of the name, possibly with spaces.'
    method_option :description, :type => :string, :required => false, :desc => 'A longer description of the state'
    method_option :status, :type => :string, :required => false, :desc => 'Optional status to bind the state to'
    def update state
      name = options.name.upcase if options.name?
      label = options.label if options.label?
      desc = options.description if options.description?
      status = options.status if options.status?
      call_collins get_collins_client, "state_update!" do |client|
        opts = {
          :name => name, :label => label, :status => status,
          :description => desc
        }
        if client.state_update!(state, opts) then
          say_success "Successfully updated state '#{state}'"
        else
          say_error "Failed creating state '#{state}'"
        end
      end
    end

  end

end
