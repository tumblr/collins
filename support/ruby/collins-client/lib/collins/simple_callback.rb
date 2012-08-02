require 'collins/util'
require 'collins/option'

module Collins

  # Represents a simple callback, e.g. something with a name, options and an exec block
  # This is designed to be a building block for other callback implementations as opposed to a
  # complete implementation. Note that we duck-type Proc. We can't extend it without running into
  # some initialize issues. We do this so people can feel free to tread the class like a proc.
  class SimpleCallback
    include ::Collins::Util

    EMPTY_NAME = :None

    # @return [Symbol] Name of the callback
    attr_reader :name

    # @return [Hash] Options specified with the callback, after normalizing
    attr_reader :options

    # @return [Collins::SimpleCallback] {#empty?} will return true
    def self.empty
      Collins::SimpleCallback.new(:none => true) {}
    end

    # Instantiate a new SimpleCallback
    #
    # @param [Array,Hash] args Arguments for instantiation
    # @yieldparam [Array<Object>] block Callback to execute, can also be specified via :block option
    #
    # @example
    #  SimpleCallback.new(:my_callback, Proc.new {|arg1| puts(arg1)})
    #  SimpleCallback.new(:my_callback, :block => Proc.new{|arg1| puts(arg1)})
    #  SimpleCallback.new :my_callback, :opt1 => "val" do |arg1|
    #    puts(arg1)
    #  end
    # 
    # @raise [ArgumentError] when name not set, and not created via #{SimpleCallback.empty}
    def initialize *args, &block
      opts = {}
      while arg = args.shift do
        if arg.is_a?(Hash) then
          opts.update(arg)
        elsif arg.respond_to?(:call) then
          opts.update(:block => arg)
        else
          key = [:name, :options].select{|k| !opts.key?(k)}.first
          opts.update(key => arg) unless key.nil?
        end
      end
      if block && block.respond_to?(:call) then
        opts.update(:block => block)
      end
      opts = symbolize_hash(opts)
      if opts.fetch(:none, false) then
        @name = EMPTY_NAME
        @block = ::Collins::None.new
        @options = {}
      else
        @name = ::Collins::Option(opts.delete(:name)).get_or_else {
          raise ArgumentError.new("SimpleCallback requires a name")
        }.to_sym
        @block = ::Collins::Option(opts.delete(:block))
        @options = opts
      end
    end

    # @see Hash
    # @param [String,Symbol] key
    # @return [Object] value associated with key
    def [](key)
      options[key.to_sym]
    end

    # @see Proc
    # @return [Fixnum] number of arguments that would not be ignored
    def arity
      block.map {|b| b.arity}.get_or_else(0)
    end
    # @see Proc
    # @return [Binding] binding associated with the proc
    def binding
      to_proc.binding
    end
    # @see Proc
    # @return [Boolean] lambda or not
    def lambda?
      block.map {|b| b.lambda?}.get_or_else(false)
    end
    # @see Proc
    # @return [Array<Array<Symbol>>] array of parameters accepted by proc
    def parameters
      block.map {|b| b.parameters}.get_or_else([])
    end
    # @see Proc
    # @return [Proc] proc or noop proc
    def to_proc
      block.map{|b| b.to_proc}.get_or_else(Proc.new{})
    end

    # @return [Collins::Option] Self as option
    def to_option
      if self.defined? then
        ::Collins::Some.new(self)
      else
        ::Collins::None.new
      end
    end

    # @return [Boolean] True if block was given to constructor
    def defined?
      name != EMPTY_NAME || block.defined?
    end
    # @return [Boolean] False if block was given to constructor
    def empty?
      name == EMPTY_NAME && block.empty?
    end

    # Call the callback block with the specified arguments
    #
    # @see Proc
    # @param [Array<Object>] args - Arguments for callback
    # @return [Object] Value from callback
    # @raise [NameError] if block wasn't specified but call was executed
    def call *args
      block.map do |b|
        b.call(*args)
      end.get
    end

    # @return [String] representation of callback
    def to_s
      "Collins::SimpleCallback(name = #{name}, options = #{options.inspect})"
    end

    protected
    # The code block associated with the simple callback
    attr_reader :block
  end

end
