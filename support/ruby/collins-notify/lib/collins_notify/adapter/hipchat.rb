module CollinsNotify

  class HipchatAdapter < Notifier
    register_name :hipchat
    supports_mimetype :text
    require_config :api_token, :username # username is the from username

    def configure!
      raise NotImplementedError.new "HipchatAdapter#configure!"
    end

    def notify! to = nil, message_obj = OpenStruct.new
      raise NotImplementedError.new "HipchatAdapter#notify!"
    end
  end

end
