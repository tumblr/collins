module CollinsNotify
  class EmailAdapter < Notifier
    register_name :email
    supports_mimetype :text, :html
    require_config

    def configure!
    end

    def notify! overrides = {}
    end
  end
end
