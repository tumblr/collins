require 'logger'

class Logger

  module Severity
    TRACE = -1
  end

  def trace?; @level <= TRACE; end

  def trace(progname = nil, &block)
    add(TRACE, nil, progname, &block)
  end

  private
  def format_severity severity
    if severity == TRACE then
      'TRACE'
    else
      SEV_LABEL[severity] || 'ANY'
    end
  end

end
