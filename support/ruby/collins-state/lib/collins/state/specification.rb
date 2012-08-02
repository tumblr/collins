require 'json'

module Collins; module State

  # Represents a managed state
  #
  # Modeling a state machine like process in collins is useful for multi-step processes such as
  # decommissioning hardware (where you want the process to span several days, with several
  # discrete steps), or monitoring some process and taking action. A
  # {Specification} provides a common format for storing state information
  # as a value of an asset.
  #
  # This will rarely be used directly, but rather is a byproduct of using
  # {Collins::State::Mixin}
  class Specification
    include ::Collins::Util

    # Used as name placeholder when unspecified
    EMPTY_NAME = :None
    # Used as description placeholder when unspecified
    EMPTY_DESCRIPTION = "Unspecified"

    # Create an empty specification
    # @return [Collins::State::Specification] spec
    def self.empty
      ::Collins::State::Specification.new :none => true
    end

    # Create an instance from JSON data
    #
    # @note This method is required by the JSON module for deserialization
    # @param [Hash] json JSON data
    # @return [Collins::State::Specification] spec
    def self.json_create json
      ::Collins::State::Specification.new json['data']
    end

    # @return [Symbol] Name of the specification.
    # @see Collins::State::Mixin::ClassMethods#event
    # @note This is a unique key and should not change.
    attr_reader :name

    # @return [String] State description, for humans
    # @see Collins::State::Mixin::ClassMethods#event
    attr_reader :description

    # @return [Fixnum] Unixtime, UTC, when this state was entered
    attr_accessor :timestamp

    # @return [Hash] Additional meta-data
    attr_reader :extras

    # Instantiate a new Specification
    #
    # @param [Hash,(Symbol,String,Fixnum)] args Arguments for instantiation
    # @option args [String,Symbol] :name The name of the specification
    # @option args [String] :description A description of the specification
    # @option args [Fixnum,Time,String] :timestamp (Time.at(0).utcto_i) The time the event occurred
    #
    # @example
    #  Specification.new :start, 'I am a state', Time.now
    #  Specification.new :start, :description => 'Hello World', :timestamp => 0
    # 
    # @note If the specified timestamp is not a `Fixnum` (unixtime), the value is converted to a fixnum
    # @raise [ArgumentError] when `timestamp` is not a `Time`, `String` or `Fixnum`
    # @raise [ArgumentError] when `name` or `description` not specified
    def initialize *args
      opts = {}
      while arg = args.shift do
        if arg.is_a?(Hash) then
          opts.update(arg)
        else
          key = [:name, :description, :timestamp].select{|k| !opts.key?(k)}.first
          opts.update(key => arg) unless key.nil?
        end
      end
      opts = symbolize_hash(opts)

      if opts.fetch(:none, false) then
        @name = EMPTY_NAME
        @description = EMPTY_DESCRIPTION
      else
        @name = ::Collins::Option(opts.delete(:name)).map{|s| s.to_sym}.get_or_else {
          raise ArgumentError.new("Name not specified")
        }
        @description = ::Collins::Option(opts.delete(:description)).get_or_else {
          raise ArgumentError.new("Description not specified")
        }
      end
      ts = ::Collins::Option(opts.delete(:timestamp)).get_or_else(Time.at(0))
      @timestamp = parse_timestamp(ts)
      # Flatten if needed
      if opts.key?(:extras) then
        @extras = opts[:extras]
      else
        @extras = opts
      end
    end

    # merges appropriate extras from the other spec into this one
    # @param [Collins::State::Specification] other
    def merge other
      ext = other.extras.merge(@extras)
      ext.delete(:none)
      @extras = ext
      self
    end

    # @return [Boolean] Indicate whether Specification is empty or not
    def empty?
      !self.defined?
    end

    # @return [Boolean] Indicate whether Specification is defined or not
    def defined?
      @name != EMPTY_NAME || @description != EMPTY_DESCRIPTION
    end

    # @return [Collins::Option] None if undefined/empty
    def to_option
      if self.defined? then
        ::Collins::Some(self)
      else
        ::Collins::None()
      end
    end

    def [](key)
      @extras[key.to_sym]
    end
    def key?(key)
      @extras.key?(key.to_sym)
    end
    def fetch(key, default)
      @extras.fetch(key.to_sym, default)
    end
    def []=(key, value)
      @extras[key.to_sym] = value
    end

    def <<(key, value)
      @extras[key.to_sym] = [] unless @extras.key?(key.to_sym)
      @extras[key.to_sym] << value
      @extras[key.to_sym]
    end

    # Convert this instance to JSON
    #
    # @note this is required by the JSON module
    # @return [String] JSON string representation of object
    def to_json(*a)
      {
        'json_class' => self.class.name,
        'data' => to_hash
      }.to_json(*a)
    end

    # @return [Hash] Hash representation of data
    def to_hash
      h = Hash[:name => name, :description => description, :timestamp => timestamp]
      h[:extras] = extras unless extras.empty?
      h
    end

    # @return [String] human readable
    def to_s
      "Specification(name = #{name}, description = #{description}, timestamp = #{timestamp}, extras = #{extras})"
    end

    # Mostly used for testing
    def ==(other)
      (other.class == self.class) &&
        other.name == self.name &&
        other.timestamp == self.timestamp
    end

    private
    def parse_timestamp ts
      if ts.is_a?(String) then
        ts.to_s.to_i
      elsif ts.is_a?(Time) then
        ts.utc.to_i
      elsif ts.is_a?(Fixnum) then
        ts
      else
        raise ArgumentError.new("timestamp is not a String, Time, or Fixnum")
      end
    end

  end # class Specification

end; end
