module WebMock
  class RequestStub
    def returns status, body = nil, headers = {}
      h = {:status => status}
      h.update(:body => body) if body
      h.update(:headers => headers.merge('Content-Type' => 'application/json'))
      to_return(h)
    end
  end
end

shared_context "collins api" do
  def username
    "tester"
  end
  def password
    "testpass"
  end
  def hostname
    "localhost:2037"
  end
  def host
    "http://#{hostname}"
  end
  def url _uri = nil
    u = _uri || uri
    "#{username}:#{password}@#{hostname}#{u}"
  end
  def method
    raise NotImplementedError.new("method not implemented")
  end
  def uri
    raise NotImplementedError.new("uri not implemented")
  end
  def api _meth = nil, _url = nil
    mth = _meth || method
    ur = _url || url
    stub_request(mth, ur)
  end
  def subject strict = true
    l = Logger.new(STDOUT)
    l.level = Logger::WARN
    Collins::Client.new :username => username, :password => password, :host => host, :strict => strict,
      :logger => l
  end
  def body hash
    value = hash.map do |k,v|
      if v.is_a?(Hash) then
        v.map{|k2,v2| "#{k}=#{k2};#{v2}"}.join('&')
      else
        "#{k}=#{v}"
      end
    end.join('&')
    URI.escape(value)
  end
end

