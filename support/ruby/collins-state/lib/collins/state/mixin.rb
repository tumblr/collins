require 'collins/state/mixin_class_methods'
require 'collins/state/specification'
require 'json'

module Collins; module State; module Mixin

  class << self
    # Classes that include Mixin will also be extended by ClassMethods
    def included(base)
      base.extend Collins::State::Mixin::ClassMethods
    end
  end

  # @abstract Classes mixing this in must supply a collins client
  # @return [Collins::Client] collins client
  # @raise [NotImplementedError] if not specified
  def collins_client
    raise NotImplementedError.new("no collins client available")
  end

  # @abstract Classes mixing this in must supply a logger
  # @return [Logger] logger instance
  # @raise [NotImplementedError] if not specified
  def logger
    raise NotImplementedError.new("no logger available")
  end

  # The attribute name used for storing the sate value
  # @note we append _json to the managed_state_name since it will serialize this way
  # @return [String] the key to use for storing the state value on an asset
  def attribute_name
    self.class.managed_state_name.to_s + "_json"
  end

  # Has the specified asset state expired?
  #
  # Read the state from the specified asset, and determine whether the state is expired yet or not.
  # The timestamp + expiration is compared to now.
  #
  # @note This method will return true if no specification is associated with the asset
  # @param [Collins::Asset] asset The asset to look at
  # @return [Boolean] True if the specification has expired, false otherwise
  def expired? asset
    specification_expired?(state_specification(asset))
  end

  # Whether we are done processing or not
  # @param [Collins::Asset] asset
  # @return [Boolean] whether asset is in done state or not
  def finished? asset
    state_specification(asset).to_option.map do |spec|
      specification_expired?(spec) && event(spec.name)[:terminus]
    end.get_or_else(false)
  end

  # The things that would be done if the asset was transitioned
  # @param [Collins::Asset] asset
  # @return [Array<Array<Symbol,String>>] array of arrays. Each sub array has two elements
  def plan asset
    plans = []
    state_specification(asset).to_option.map { |specification|
      event(specification.name).to_option.map { |ev|
        if not specification_expired?(specification) then
          plans << [:noop, "not yet expired"]
        else
          if ev[:transition] then
            event(ev[:transition]).to_option.map { |ev2|
              plans << [:event, ev2.name]
            }.get_or_else {
              plans << [:exception, "invalid event name #{ev[:transition]}"]
            }
          elsif not ev[:on_transition] then
            plans << [:noop, "no transition specified, and no on_transition action specified"]
          end
          if ev[:on_transition] then
            action(ev[:on_transition]).to_option.map { |ae|
              plans << [:action, ae.name]
            }.get_or_else {
              plans << [:exception, "invalid action specified #{ev[:on_transition]}"]
            }
          end
        end
      }.get_or_else {
        plans << [:exception, "invalid event name #{e.name}"]
      }
    }.get_or_else {
      Collins::Option(initial).map { |init|
        event(init).to_option.map { |ev|
          if ev[:before_transition] then
            action(ev[:before_transition]).to_option.map do |act|
              plans << [:action, act.name]
            end.get_or_else {
              plans << [:exception, "action #{ev[:before_transition]} not defined"]
            }
          end
          plans << [:event, init]
        }.get_or_else {
          plans << [:exception, "initial state #{init} is undefined"]
        }
      }.get_or_else {
        plans << [:exception, "no initial state defined"]
      }
    }
    plans
  end

  # Reset (delete) the attribute once the process is complete
  #
  # @param [Collins::Asset] asset The asset on which to delete the attribute
  # @return [Boolean] True if the value was successfully deleted
  def reset! asset
    collins_client.delete_attribute! asset, attribute_name
  end

  # Return the name of the current sate. Will be :None if not initialized
  # @param [Collins::Asset] asset
  # @return [Symbol] state name
  def state_name asset
    state_specification(asset).name
  end

  # Get the state specification associated with the asset
  #
  # @param [Collins::Asset] asset The asset to retrieve
  # @return [Collins::State::Specification] The spec (be sure to check `defined?`)
  def state_specification asset
    updated = asset_from_cache asset
    result = updated.send(attribute_name.to_sym)
    if result then
      res = JSON.parse(result, :create_additions => false) rescue nil
      if not res.nil? then
        res = ::Collins::State::Specification.json_create(res) if (res.is_a?(Hash) and res.key?('data'))
      end

      if res.is_a?(::Collins::State::Specification) then
        res
      else
        logger.warn("Could not deserialize #{result} to a State Specification")
        ::Collins::State::Specification.empty
      end
    else
      ::Collins::State::Specification.empty
    end
  end

  # Transition the asset to the next appropriate state
  #
  # This method will either initialize the state on the specified asset (if needed), or process the
  # current state. If processing the current state, the expiration time will be checked, followed by
  # running any specified transition event, followed by any `on_transition` action. The transition
  # event is run before the `on_transition` action, because the `on_transition` action should only be
  # called if we have successfully transitioned to a new state. In the event that the transition
  # event has a `before_transition` defined that fails, we don't want to execute the `on_transition`
  # code since we have not yet successfully transitioned.
  #
  # @param [Collins::Asset] asset The asset to transition
  # @param [Hash] options Transition options
  # @option options [Boolean] :quiet Don't throw an exception if a transition fails
  # @raise [CollinsError] if state needs to be initialized and no `:initial` key is found in the manage_state options hash
  # @raise [CollinsError] if a specification is found on the asset, but the named state isn't found as a registered event
  # @raise [CollinsError] if an action is specified as a `:before_transition` or `:on_transition` value but not registered
  # @return [Collins::State::Specification] The current state, after the transition is run
  def transition asset, options = {}
    state_specification(asset).to_option.map { |specification|
      event(specification.name).to_option.or_else {
        raise CollinsError.new("no event defined with name #{specification.name}")
      }.filter { |e| specification_expired?(specification) }.map { |e|
        if e[:transition] then
          spec = run_event(asset, e[:transition], options)
          run_action(asset, e[:on_transition]) if e[:on_transition]
          # If we transitioned and no expiration is set, rerun
          if specification_expired?(spec) and spec.name != e.name then
            transition(asset, options)
          else
            spec
          end
        else
          logger.debug("No transition event specified for #{e.name}")
          run_action(asset, e[:on_transition], :log => true) if e[:on_transition]
          specification
        end
      }.get_or_else {
        logger.trace("Specification #{specification.name} not yet expired")
        specification
      }
    }.get_or_else {
      init = Collins::Option(initial).get_or_else {
        raise Collins::CollinsError.new("no initial state defined for transition")
      }
      options = Collins::Option(self.class.managed_state_options).get_or_else({})
      run_event(asset, init, options)
    }
  end

  # Allow registered events to be executed. Allow predicate calls to respond true if the asset is
  # currently in the specified state or false otherwise
  def method_missing method, *args, &block
    if args.length == 0 then
      return super
    end
    asset = args[0]
    options = args[1]
    if not (asset.is_a?(Collins::Asset) || asset.is_a?(String)) then
      return super
    end
    if not options.nil? and not options.is_a?(Hash) then
      return super
    elsif options.nil? then
      options = {}
    end
    question_only = method.to_s.end_with?('?')
    if question_only then
      meth = method.to_s[0..-2].to_sym # drop ? at end
      if event?(meth) then
        state_name(asset) == meth
      else
        false
      end
    elsif event?(method) then
      run_event(asset, method, options)
    else
      super
    end
  end # method_missing

  def respond_to? method
    question_only = method.to_s.end_with?('?')
    if question_only then
      method = method.to_s[0..-2] # drop? at end
    end
    if not event?(method) then
      super
    else
      true
    end
  end # respond_to?

  protected
  # Get the callback associated with the specified action name
  # @param [Symbol] name Action name
  # @return [Collins::SimpleCallback] always returns, check `defined?`
  def action name
    name_sym = name.to_sym
    self.class.actions.fetch(name_sym, ::Collins::SimpleCallback.empty)
  end

  # True if an event with the given name is registered, false otherwise
  # @param [Symbol] name Event name
  # @return [Boolean] Registered or not
  def event? name
    event(name).defined?
  end

  # Get the callback associated with the specified event name
  # @param [Symbol] name Event name
  # @return [Collins::SimpleCallback] always returns, check `defined?`
  def event name
    name_sym = name.to_sym
    self.class.events.fetch(name_sym, ::Collins::SimpleCallback.empty)
  end

  # Get the expires time associated with the specified event
  #
  # @param [Symbol] name Name of the event
  # @param [Fixnum] default Value if event not found or expires not specified
  # @return [Fixnum] An integer representing the number of seconds before expiration
  def event_expires name, default = 0
    event(name).to_option.map do |e|
      e.options.fetch(:expires, default.to_i)
    end.get_or_else(default.to_i)
  end

  # Initial event or nil
  def initial
    Collins::Option(self.class.managed_state_options).
      filter{|h| h.key?(:initial)}.
      map{|h| h[:initial]}.
      get_or_else(nil)
  end

  # Run the specified action
  #
  # @param [Collins::Asset] asset the asset to run the action for
  # @param [Symbol] name the action name
  # @param [Hash] options
  # @option options [Boolean] :log (true) log action run
  # @return [Boolean] the result of executing the action
  def run_action asset, name, options = {}
    action(name).to_option.map do |a|
      result = case a.arity
                when 2
                  a.call(asset, self)
                else
                  a.call(asset)
                end
      log_run_action(asset, name, result) if options.fetch(:log, false)
      result
    end.get_or_else {
      raise CollinsError.new("Action #{name} not defined")
    }
  end

  # Update the asset spec that the action was run
  # @param [Collins::Asset] asset
  # @param [Symbol] action_name
  # @param [Boolean] result of the action that was run
  # @return [Collins::State::Specification]
  def log_run_action asset, action_name, result
    current_spec = state_specification asset
    count = current_spec.fetch(:log, []).size
    log = Hash[:count => count, :timestamp => Time.now.utc.to_i, :name => action_name, :result => result]
    current_spec.<<(:log, log)
    asset_cache_delete asset
    update_asset asset, attribute_name, current_spec
    current_spec
  end

  # Run the specified event, and execute :before_transition if specified
  #
  # @param [Collins::Asset] asset the asset to process the event for
  # @param [Symbol] name the name of the event to process
  # @param [Hash] options Option for executing the event
  # @option options [Boolean] :quiet Do not throw an exception if an error occurs running actions
  # @return [Collins::State::Specification] the new state the asset is in
  def run_event asset, name, options = {}
    event(name).to_option.map do |e|
      update_state(asset, e, options)
    end.get_or_else {
      raise CollinsError.new("Event #{name} not defined")
    }
  end

  # Whether the given specification has expired or not
  #
  # This method is primarily useful for testing whether a stored specification has expired yet or
  # not. If a specification has just been created (and has an expiration set), this will obviously
  # return false.
  #
  # @param [Collins::State::Specification] specification
  # @return [Boolean] true if expired, false otherwise
  def specification_expired? specification
    timestamp = specification.timestamp
    name = specification.name
    expires_at = timestamp + event_expires(name)
    # Must be >= otherwise 0 + Time.now as expires_at will fail
    Time.now.utc.to_i >= expires_at
  end

  # Update asset state using the specified event information
  #
  # This method also handles executing appropriate transition related actions and managing failures.
  # The actual code for updating the asset itself is in #update_asset
  #
  # @param [Collins::Asset] asset the asset to update
  # @param [Collins::SimpleCallback] event the event
  # @param [Hash] options Option for executing the event
  # @option options [Boolean] :quiet Do not throw an exception if an error occurs running actions
  def update_state asset, event, options = {}
    if event[:before_transition] then
      run_before_transition asset, event[:before_transition], event, options
    else
      specification = Collins::State::Specification.new event.name, event[:desc], Time.now
      specification = specification.merge(state_specification(asset))
      asset_cache_delete asset
      res = update_asset asset, attribute_name, specification
      # Horrible hack to allow update_asset to be overridden such that it can provide a string
      # for performing an update, in which case we need the actual command
      if res.is_a?(String) then
        return res
      else
        return specification
      end
    end
  end

  # Run the specified before_transition
  # We try running the specified action, and if successful update the asset with the specification.
  # If running the action fails, we update the attempts count on the current specification and
  # either throw an exception (default behavior) or return the updated spec (options[:quiet] ==
  # true). On success we return the new specification.
  def run_before_transition asset, action_name, event, options
    before_result = 
      begin
        run_action(asset, action_name)
      rescue Exception => e
        logger.warn("Failed running #{action_name}: #{e}")
        false
      end
    if before_result != false
      specification = Collins::State::Specification.new event.name, event[:desc], Time.now
      specification = specification.merge(state_specification(asset))
      asset_cache_delete asset
      update_asset asset, attribute_name, specification
      specification
    else
      current_spec = state_specification(asset)
      count = current_spec.fetch(:attempts, []).size
      attempts = Hash[:timestamp => Time.now.utc.to_i, :count => count, :name => event.name]
      current_spec.<<(:attempts, attempts)
      asset_cache_delete asset
      update_asset asset, attribute_name, current_spec
      if options.fetch(:quiet, false) then
        return current_spec
      else
        raise CollinsError.new("Can't transition from #{current_spec.name} to #{event.name}, before_transition failed")
      end
    end
  end

  # Update the asset using the specified key and JSON
  #
  # This method is broken out this way so it can be easily overriden
  # @param [Collins::Asset] asset The asset to update
  # @param [String] key The attribute to update on the asset
  # @param [Specification] spec The state specification to set as the value associated with the key
  # @return [Boolean] indication of success or failure
  def update_asset asset, key, spec
    collins_client.set_attribute! asset, key, spec.to_json
  end

  private
  def asset_cache_delete asset
    tag = Collins::Util.get_asset_or_tag(asset).tag
    @_asset_cache.delete(tag) if (@_asset_cache && @_asset_cache.key?(tag))
  end
  def asset_from_cache asset
    tag = Collins::Util.get_asset_or_tag(asset).tag
    @_asset_cache = {} unless @_asset_cache
    if not @_asset_cache.key?(tag) then
      @_asset_cache[tag] = collins_client.get(tag)
    end
    @_asset_cache[tag]
  end

end; end; end
