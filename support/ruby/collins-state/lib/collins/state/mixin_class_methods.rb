require 'collins/simple_callback'

module Collins; module State; module Mixin

  # Static (Class) methods to be added to classes that mixin this module. These methods provide
  # functionality for registering new actions and events, along with access to the managed state.
  # This is accomplished via class instance variables which allow each class that include the
  # {Collins::State::Mixin} its own managed state and variables.
  module ClassMethods

    # @return [Hash<Symbol, Collins::SimpleCallback>] Registered actions for the managed state
    attr_reader :actions

    # @return [Hash<Symbol, Collins::SimpleCallback>] Registered events for the managed state
    attr_reader :events

    # Register a managed state for this class
    #
    # @note Only one managed state per class is allowed.
    # @param [Symbol] name The name of the managed state, e.g. `:some_process`
    # @param [Hash] options Managed state options
    # @option options [Symbol] :initial First event to fire if the state is being initialized
    # @yieldparam [Collins::Client] block Managed state description, registering events and actions
    #
    # @example
    #  manage_state :a_process, :initial => :start do |client|
    #    action :log_it do |asset|
    #      client.log! asset, "Did some logging"
    #    end
    #    event :start, :desc => 'Initial state', :expires => after(30, :minutes), :on_transition => :log_it, :transition => :done
    #    event :done, :desc => 'Done'
    #  end
    def manage_state name, options = {}, &block
      @actions = {}
      @events = {}
      @managed_state = ::Collins::SimpleCallback.new(name, options, block)
    end

    # @return [String] Name of managed state associated with this class
    def managed_state_name
      @managed_state.name
    end

    # @return [Hash] Options associated with this managed state
    def managed_state_options
      @managed_state.options
    end

    # Get and execute the managed state associated with this class
    #
    # @see {#manage_state}
    # @param [Collins::Client] client Collins client instance
    def managed_state client
      @managed_state.call(client)
    end

    # Register a named action for use as a transition execution target
    #
    # Actions are called when specified by a `:before_transition` or `:on_transition` option to an
    # event. Options are not currently used.
    #
    # @param [Symbol] name Action name
    # @param [Hash] options Action options
    # @yieldparam [Collins::Asset] block Given an asset, perform the specified action
    # @yieldreturn [Boolean,Object] indicates success or failure of operation. Only `false` (or an exception) indicate failure. Other return values are fine.
    # @return [Collins::SimpleCallback] the callback created for the action
    def action name, options = {}, &block
      name_sym = name.to_sym
      new_action = ::Collins::SimpleCallback.new(name_sym, options, block)
      @actions[name_sym] = new_action
      new_action
    end

    # Register a named event associated with the managed state
    #
    # Events are typically called as method invocations on the class, or as the result of a state
    # being expired and a transition being specified.
    #
    # The example (and events in general) can be read as: Execute `:before_transition` before
    # successfully transitioning to this event. Once transitioned, after `:expires` is reached the
    # `:on_transition` action should be called, followed by the event associated with the specified
    # `:transition`.
    #
    # @param [Symbol] name Event name
    # @param [Hash] options Event options
    # @option options [Symbol] :before_transition An action to execute successfully before transitioning to this state
    # @option options [String] :desc A description of the event, required
    # @option options [#to_i] :expires Do not consider `:on_transition` or `:transition` until after this amount of time
    # @option options [Symbol] :on_transition Once the expiration time has passed execute this action
    # @option options [Symbol] :transition The event to call after its appropriate for transition (due to timeout and successful `:before` calls)
    # @return [Collins::SimpleCallback] the callback created for the action
    #
    # @example
    #  event :stuff, :desc => 'I do things', :before_transition => :try_action, :expires => after(5, :minutes),
    #                :transition => :after_stuff_event, :on_transition => :stuff_action
    #
    # @note A transition will not occur unless the :before_transition is successful (does not return false or throw an exception)
    # @raise [KeyError] if the options hash is missing a `:desc` key
    def event name, options = {}
      name_sym = name.to_sym
      ::Collins::Option(options[:desc]).or_else {
        raise KeyError.new("Event #{name} is missing :desc key")
      }
      new_event = ::Collins::SimpleCallback.new(name_sym, options)
      @events[name_sym] = new_event
      new_event
    end

    # Convert a `Fixnum` into seconds based on the specified `time_unit`
    #
    # @param [Fixnum] duration Time value
    # @param [Symbol] time_unit Unit of time, one of `:hour`, `:hours`, `:minute`, `:minutes`.
    # @return [Fixnum] Value in seconds
    def after duration, time_unit = :seconds
      multiplier = case time_unit
                   when :days, :day
                     24*60*60
                   when :hours, :hour
                     60*60
                   when :minutes, :minute
                     60
                   else
                     1
                   end
      multiplier * duration.to_i
    end

  end # Collins::State::Mixin::ClassMethods

end; end; end
