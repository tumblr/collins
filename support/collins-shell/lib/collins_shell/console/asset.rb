module CollinsShell; module Console
  class Asset
    include CollinsShell::Console::CommandHelpers

    attr_reader :tag
    def initialize asset
      @tag = asset
      @asset_client = collins_client.with_asset(@tag)
    end
    def power! action = nil
      Collins::Option(action).map do |action|
        action = Collins::Power.normalize_action(action)
        verifying_response("power #{action}") {
          @asset_client.power!(action)
        }
      end.get_or_else {
        cput("A power action argument is required. power <action>")
      }
    end
    def reboot! how = "rebootSoft"
      Collins::Option(how).map do |how|
        action = Collins::Power.normalize_action(how)
        verifying_response("reboot") {
          @asset_client.power!(action)
        }
      end.get_or_else {
        cput("A reboot argument is required. reboot <action>")
      }
    end
    def stat
      s = <<-STAT
         Asset: #{underlying.tag}
        Status: #{underlying.status}
          Type: #{underlying.type}
      Hostname: #{Collins::Option(underlying.hostname).get_or_else("(none)")}
       Created: #{Collins::Option(underlying.created).get_or_else("(none)")}
       Updated: #{Collins::Option(underlying.updated).get_or_else("(none)")}
       Deleted: #{Collins::Option(underlying.deleted).get_or_else("(none)")}
      STAT
      cput(s)
    end
    def set_status! status = nil, reason = nil
      msg = "set_status request a %s. set_status <status>, <reason>"
      (raise sprintf(msg, "status")) if status.nil?
      (raise sprintf(msg, "reason")) if reason.nil?
      verifying_response("set the status to '#{status}' on") {
        @asset_client.set_status!(status, reason)
      }
    end
    def log! msg, level = nil
      (raise "log requires a message. log <message>") if (msg.nil? || msg.to_s.empty?)
      @asset_client.log!(msg, level)
    end
    def logs options = {}
      @asset_client.logs(options)
    end
    def set! key = nil, value = nil, group_id = nil
      msg = "set requires a %s. set <key>, <value>"
      (raise sprintf(msg, "key")) if key.nil?
      (raise sprintf(msg, "value")) if value.nil?
      case value
      when String, Symbol, Fixnum, TrueClass, FalseClass then
        value = value.to_s
      else
        raise "value can't be a #{value.class}"
      end
      verifying_response("set the key '#{key}' to '#{value}' on") {
        @asset_client.set_attribute!(key.to_s, value, group_id)
      }
    end
    def rm! key = nil, group_id = nil
      (raise "rm requires a key. rm <key>") if key.nil?
      verifying_response("delete the key '#{key}' on") {
        @asset_client.delete_attribute!(key, group_id)
      }
    end
    def key? key = nil
      (raise "key? requires a key. key? <key>") if key.nil?
      @asset_client.get.send("#{key}?".to_sym)
    end
    def key key = nil
      (raise "key requires a key. key <key>") if key.nil?
      @asset_client.get.send(key.to_sym)
    end
    def on?
      power? == "on"
    end
    def power?
      @asset_client.power_status
    end
    def respond_to? meth, include_private = false
      if meth.to_sym == :asset then
        true
      elsif @asset_client.respond_to?(meth) then
        true
      else
        super
      end
    end
    protected
    def method_missing meth, *args, &block
      if meth.to_sym == :asset then
        underlying
      elsif @asset_client.respond_to?(meth) then
        @asset_client.send(meth, *args, &block)
      else
        super
      end
    end
    def underlying
      @underlying ||= get_asset(@tag)
    end
    def verifying_response message, &block
      message = "You are about to #{message} asset #{tag}. Are you sure? "
      if shell_handle.require_yes(message, :red, false) then
        block.call
      else
        cput("Aborted operation")
      end
    end
    def cput message
      Pry.output.puts(message)
    end

  end # class CollinsShell::Console::Asset

end; end
