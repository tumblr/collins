require 'collins/monkeypatch'
require 'collins/option'

module Collins; module Util

  module Logging

    DEFAULT_LOG_FORMAT = "%Y-%m-%d %H:%M:%S.%L"

    def get_logger options = {}
      return options[:logger] if options[:logger]
      trace = Collins::Option(options[:trace]).get_or_else(false)
      debug = Collins::Option(options[:debug]).get_or_else(false)
      progname = Collins::Option(options[:progname] || options[:program]).get_or_else('unknown')
      logfile = Collins::Option(options[:logfile]).get_or_else(STDOUT)
      logger = Logger.new(logfile)
      if trace then
        logger.level = Logger::TRACE
      elsif debug then
        logger.level = Logger::DEBUG
      else
        logger.level = Logger::INFO
      end
      logger.progname = File.basename(progname)
      logger.formatter = Proc.new do |severity, datetime, progname, message|
        date_s = datetime.strftime(Collins::Util::Logging::DEFAULT_LOG_FORMAT)
        "#{severity} [#{date_s}] #{progname}: #{message}\n"
      end
      logger
    end
  end

end; end
