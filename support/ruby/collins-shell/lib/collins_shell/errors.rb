module CollinsShell

  class CollinsShellError < StandardError; end
  class ConfigurationError < CollinsShellError; end
  class RequirementFailedError < CollinsShellError; end

end
