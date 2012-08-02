require 'collins/state/mixin'
require 'escape'

module Collins

  # Provides state management via collins tags
  class PersistentState

    include ::Collins::State::Mixin
    include ::Collins::Util

    attr_reader :collins_client, :path, :exec_type, :logger

    def initialize collins_client, options = {}
      @collins_client = collins_client
      @exec_type = :client
      @logger = get_logger({:logger => collins_client.logger}.merge(options).merge(:progname => 'Collins_PersistentState'))
    end

    def run
      self.class.managed_state(collins_client)
      self
    end

    # @deprecated Use {#use_netcat} instead. Replace in 0.3.0
    def use_curl path = nil
      use_netcat(path)
    end

    def use_client
      @path = nil
      @exec_type = :client
      self
    end

    def use_netcat path = nil
      @path = Collins::Option(path).get_or_else("nc")
      @exec_type = :netcat
      self
    end

    # @override update_asset(asset, key, spec)
    def update_asset asset, key, spec
      username = collins_client.username
      password = collins_client.password
      host = collins_client.host
      tag = ::Collins::Util.get_asset_or_tag(asset).tag
      case @exec_type
      when :netcat
        netcat_command = [path, '-i', '1'] + get_hostname_port(host)
        timestamp_padding, json = format_spec_for_netcat spec
        body = "attribute=#{key};#{json}"
        length = body.size + timestamp_padding
        request = [request_line(tag)] + request_headers(username, password, length)
        request_string = request.join("\\r\\n") + "\\r\\n\\r\\n" + body
        current_time = 'TIMESTAMP=$(' + get_time_cmds(host).join(' | ') + ')'
        args = ['printf', request_string]
        "#{current_time}\n" + Escape.shell_command(args) + ' $TIMESTAMP | ' + Escape.shell_command(netcat_command)
      else
        super(asset, key, spec)
      end
    end

    def get_time_cmds host
      hostname, port = get_hostname_port host
      get_cmd = %q{printf 'GET /api/timestamp HTTP/1.0\r\n\r\n'}
      nc = sprintf('%s -i 1 %s %d', path, hostname, port)
      only_time = %q{grep -e '^[0-9]'}
      last_line = %q{tail -n 1}
      [get_cmd, nc, only_time, last_line]
    end

    def request_line tag
      "POST /api/asset/#{tag} HTTP/1.0"
    end

    def request_headers username, password, length
      [
        "User-Agent: collins_state",
        auth_header(username, password),
        "Content-Type: application/x-www-form-urlencoded",
        "Content-Length: #{length}",
        "Connection: Close"
      ]
    end

    def auth_header username, password
      auth = Base64.strict_encode64("#{username}:#{password}")
      "Authorization: Basic #{auth}"
    end

    # @return [Array<Fixnum,String>] padding for format string and json to use
    def format_spec_for_netcat spec
      expected_timestamp_size = Time.now.utc.to_i.to_s.size
      spec.timestamp = '%s'
      actual_timestamp_size = spec.timestamp.to_s.size
      timestamp_padding = (expected_timestamp_size - actual_timestamp_size).abs
      json = spec.to_json
      [timestamp_padding, json]
    end
    # @return [Array<String,String>] hostname and port
    def get_hostname_port host
      host = URI.parse(host)
      [host.host, host.port.to_s]
    end
  end

end
