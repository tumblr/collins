require "addressable/uri"
require "socket"
require "openssl"

# Code is largely ripped off from https://github.com/portertech/carrier-pigeon which carriers an MIT
# license. Carrier pigeon offers no ability to detect a 433 response (nickname already in use) and
# automatically fix it. There's also not much in the way of debugging. That's all that is added
# here.
class CarriedPigeon

  def self.send(options={})
    raise "You must supply a message" unless options[:message]
    if options[:uri] then
      uri = Addressable::URI.parse(options[:uri])
      options[:host] = uri.host
      options[:port] = uri.port || 6667
      options[:nick] = uri.user
      options[:password] = uri.password
      unless options[:channel] then
        options[:channel] = "#" + uri.fragment
      end
    end
    pigeon = CarriedPigeon.new options
    pigeon.message options[:message], options[:notice]
    pigeon.die
  end

  attr_reader :connected, :logger, :options
  def initialize(options={})
    [:host, :port, :nick, :channel, :logger].each do |option|
      raise "You must provide an IRC #{option}" unless options.has_key?(option)
    end
    @logger = options[:logger]
    @options = options
    connect! if (options.fetch(:connect, true)) # default to connect, same as old behavior
  end

  def auth
    # Only auth if not connected and password is specified
    if connected? or !password then
      return false
    end
    # Must be first according to RFC 2812
    sendln "PASS #{password}", :log
    sendln "USER #{nick} 0 * :#{real_name}", :log
    true
  end

  def connect!
    return false if connected?
    tcp_socket = TCPSocket.new(host, port)
    if ssl? then
      ssl_context = OpenSSL::SSL::SSLContext.new
      ssl_context.verify_mode = OpenSSL::SSL::VERIFY_NONE
      @socket = OpenSSL::SSL::SSLSocket.new(tcp_socket, ssl_context)
      @socket.sync = true
      @socket.sync_close = true
      @socket.connect
    else
      @socket = tcp_socket
    end
    if auth? and not auth then
      die
      raise "Could not authenticate"
    end
    set_nick!
    @connected = true
    issue_nickserv_command
    register
  end

  def message message, notice = false, opts = {}
    command = notice ? "NOTICE" : "PRIVMSG"
    # Reset join/channel/channel_password options if specified
    options[:join] = opts[:join] if opts.key?(:join)
    options[:channel] = opts[:channel] if opts.key?(:channel)
    if opts.key?(:channel) then
      options[:channel_password] = opts[:channel_password]
    end
    join!
    sendln "#{command} #{channel} :#{message}", :log
  end

  def die
    begin
      sendln "QUIT :quit", :log
      socket.gets until socket.eof?
    rescue Exception => e
      logger.error "Error quitting IRC - #{e}"
    ensure
      socket.close
    end
  end

  protected
  attr_accessor :socket

  def auth?; options.key?(:password); end
  def channel_password?; options.key?(:channel_password); end
  def connected?; (connected == true); end
  def join?; (options[:join] == true); end
  def nickserv_command?; options.key?(:nickserv_command); end
  def nickserv_password?; options.key?(:nickserv_password); end
  def register_first?; (options[:register_first] == true); end
  def ssl?; (options[:ssl] == true); end

  def channel; options[:channel]; end
  def channel_password; options[:channel_password]; end
  def host; options[:host]; end
  def mode; options.fetch(:mode, 0); end
  def nick; options[:nick]; end
  def nickserv_command; options[:nickserv_command]; end
  def nickserv_password; options[:nickserv_password]; end
  def password; options[:password]; end
  def port; options[:port]; end
  def real_name; options.fetch(:real_name, nick); end

  # Where a response was an error or not
  def error? response
    rc = response_code response
    rc >= 400 && rc < 600
  end

  # Generate a nickname, useful if yours is taken
  def gen_nick name
    names = name.split('_')
    if names.size == 1 then
      "#{name}_1"
    elsif names.last.to_i > 0 then
      num = names.pop.to_i
      if num > 10 then
        raise "Too many nicknames generated (10)"
      end
      "#{names.join('_')}_#{num + 1}"
    else
      "#{name}_1"
    end
  end

  def issue_nickserv_command
    if nickserv_password? then
      sendln "PRIVMSG NICKSERV :IDENTIFY #{nickserv_password}"
    elsif nickserv_command? then
      sendln nickserv_command
    end
  end

  def join!
    return unless join?
    join = "JOIN #{channel}"
    join += " #{channel_password}" if channel_password?
    sendln join, :log
  end

  def log_response
    response = socket.gets
    if error? response then
      logger.error "IRC response: #{response.strip}"
    else
      logger.trace "IRC response: #{response.strip}"
    end
    response
  end

  def register
    if register_first?
      while line = socket.gets
        case line
          when /^PING :(.+)$/i
            sendln "PONG :#{$1}"
            break
        end
      end
    end
  end

  # 0 on no response code, otherwise integer
  def response_code response
    response.to_s.split[1].to_i
  end

  # Send an IRC command to a socket, second arg can be one of :log or :none
  # :log - log the response and get it
  # :none - do nothing
  def sendln cmd, log_or_get = :none
    logger.trace "IRC command : #{cmd}"
    r = socket.puts cmd
    case log_or_get
    when :log
      log_response
    else
      r
    end
  end

  def set_nick! name = nil
    if name.nil? then
      name = nick
    end
    response = sendln "NICK #{name}", :log
    rc = response_code response
    if rc == 433 then
      logger.info "Generating new NICK to connect with"
      set_nick! gen_nick(name)
    elsif error?(response) then
      raise "Unable to set nick to #{name} - #{response}"
    else
      # Reset nick if needed
      options[:nick] = name
    end
  end

end
