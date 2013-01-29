module CollinsNotify
  class EmailAdapter < Notifier
    register_name :email
    supports_mimetype :text, :html
    require_config :delivery_method # options besides delivery_method are specific to delivery_method

    def configure!
      raise NotImplementedError.new "EmailAdapter#configure!"
    end

    def notify! to = nil, message_obj = OpenStruct.new
      raise NotImplementedError.new "EmailAdapter#notify!"
    end
  end
end
