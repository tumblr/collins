module CollinsNotify

  class HipchatAdapter < Notifier
    register_name :hipchat
    supports_mimetype :text
    require_config
  end

end
