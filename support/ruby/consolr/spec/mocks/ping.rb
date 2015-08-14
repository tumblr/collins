module MockPingMixin
  require 'net/ping'

  def mock_ping
    allow_any_instance_of(Net::Ping::External).to receive(:ping?).
                                                   and_return(true)

  end

end
