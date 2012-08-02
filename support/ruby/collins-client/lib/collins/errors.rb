module Collins

  class CollinsError < StandardError; end
  class ExpectationFailedError < CollinsError; end
  class UnexpectedResponseError < CollinsError; end
  class RequestError < CollinsError
    attr_accessor :code, :uri
    def initialize message, code
      super(message)
      @code = code.to_i
    end
    def description verbose = false
      <<-D
      #{message}
      Response Code: #{code}
      URI: #{uri}
      D
    end
  end

  class RichRequestError < RequestError
    attr_accessor :class_of, :remote_description, :remote_message, :stacktrace
    def initialize message, code, description, details = {}
      super(message, code)
      @code = code
      @remote_description = description
      @class_of = details["classOf"]
      @remote_message = details["message"]
      @stacktrace = details["stackTrace"]
    end
    def get_remote_stacktrace verbose
      if verbose then
        stacktrace
      else
        "Suppressed"
      end
    end
    def description verbose = false
      <<-D
      #{message}
      Response Code: #{code}
      URI: #{uri}
      Remote Description: #{remote_description}
      Remote Exception Class: #{class_of}
      Remote Message:
      #{remote_message}

      Remote Backtrace:
      #{get_remote_stacktrace(verbose)}
      D
    end
  end

  class AuthenticationError < CollinsError; end

end
