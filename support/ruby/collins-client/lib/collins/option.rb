module Collins

  # Convenience method for creating an `Option`
  def self.Option value
    if value.nil? then
      ::Collins::None()
    else
      ::Collins::Some(value)
    end
  end
  # Convenience method for creating a `None`
  def self.None
    ::Collins::None.new
  end
  # Convenience method for creating a `Some`
  def self.Some value
    ::Collins::Some.new(value)
  end

  # Represents optional values. Instances of `Option` are either an instance of `Some` or `None`
  # @note This is pretty much a straight rip off of the scala version
  # @example
  #   name = get_parameter("name")
  #   upper = Option(name).map{|s| s.strip}.filter{|s|s.size > 0}.map{|s|s.upcase}
  #   puts(upper.get_or_else(""))
  class Option

    # @return [Boolean] True if the value is undefined
    def empty?
      raise NotImplementedError.new("empty? not implemented")
    end

    # @return [Boolean] True if the value is defined
    def defined?
      !empty?
    end

    # @return [Object] Value, if defined
    # @raise [NameError] if value is undefined
    def get
      raise NotImplementedError.new("get not implemented")
    end

    # The value associated with this option, or the default
    #
    # @example
    #  # Raises an exception
    #  Option(nil).get_or_else { raise Exception.new("Stuff") }
    #  # Returns -1
    #  Option("23").map {|i| i.to_i}.filter{|i| i > 25}.get_or_else -1
    #
    # @param [Object] default A default value to use if the option value is undefined
    # @yield [] Provide a default with a block instead of a parameter
    # @return [Object] If None, default, otherwise the value
    def get_or_else *default
      if empty? then
        if block_given? then
          yield
        else
          default.first
        end
      else
        get
      end
    end

    # Return this `Option` if non-empty, otherwise return the result of evaluating the default
    # @example
    #  Option(nil).or_else { "foo" } == Some("foo")
    # @return [Option<Object>]
    def or_else *default
      if empty? then
        res = if block_given? then
                yield
              else
                default.first
              end
        if res.is_a?(Option) then
          res
        else
          ::Collins::Option(res)
        end
      else
        self
      end
    end

    # Return true if non-empty and predicate is true for the value
    # @return [Boolean] test passed
    def exists? &predicate
      !empty? && predicate.call(get)
    end

    # Apply the block specified to the value if non-empty
    # @return [NilClass]
    def foreach &f
      if self.defined? then
        f.call(get)
      end
      nil
    end

    # If the option value is defined, apply the specified block to that value
    #
    # @example
    #  Option("15").map{|i| i.to_i}.get == 15
    #
    # @yieldparam [Object] block The current value
    # @yieldreturn [Object] The new value
    # @return [Option<Object>] Optional value
    def map &block
      if empty? then
        None.new
      else
        Some.new(block.call(get))
      end
    end

    # Same as map, but flatten the results
    #
    # This is useful when operating on an object that will return an `Option`.
    #
    # @example
    #   Option(15).flat_map {|i| Option(i).filter{|i2| i2 > 0}} == Some(15)
    #
    # @see #map
    # @return [Option<Object>] Optional value
    def flat_map &block
      if empty? then
        None.new
      else
        res = block.call(get)
        if res.is_a?(Some) then
          res
        else
          Some.new(res)
        end
      end
    end

    # Convert to `None` if predicate fails
    #
    # Returns this option if it is non-empty *and* applying the predicate to this options returns
    # true. Otherwise return `None`.
    #
    # @yieldparam [Object] predicate The current value
    # @yieldreturn [Boolean] result of testing value
    # @return [Option<Object>] `None` if predicate fails, or already `None`
    def filter &predicate
      if empty? || predicate.call(get) then
        self
      else
        None.new
      end
    end

    # Inverse of `filter` operation.
    #
    # Returns this option if it is non-empty *and* applying the predicate to this option returns
    # false. Otherwise return `None`.
    #
    # @see #filter
    # @return [Option<Object>]
    def filter_not &predicate
      if empty? || !predicate.call(get) then
        self
      else
        None.new
      end
    end
  end

  # Represents a missing value
  class None < Option
    # Always true for `None`
    # @see Option#empty?
    def empty?
      true
    end
    # Always raises a NameError
    # @raise [NameError]
    def get
      raise NameError.new("None.get")
    end
    def eql? other
      self.class.equal?(other.class)
    end
    alias == eql?
  end

  # Represents a present value
  #
  # A number of equality and comparison methods are implemented so that `Some` values are compared
  # using the value of `x`.
  class Some < Option
    def initialize value
      @x = value
    end
    def empty?
      false
    end
    def get
      x
    end
    def eql? other
      self.class.equal?(other.class) && x.eql?(other.x)
    end
    alias == eql?
    def hash
      x.hash
    end
    def <=>(other)
      self.class == other.class ?
        (x <=> other.x) : nil
    end
    protected
    attr_reader :x
  end

end
