module MockCollinsAuthMixin
  require 'collins_auth'
  require 'collins_client'

  def mock_collins_auth
    allow_any_instance_of(Collins::Authenticator).to receive(:setup_client).
                                                     and_return(Collins::Client)

  end

end
