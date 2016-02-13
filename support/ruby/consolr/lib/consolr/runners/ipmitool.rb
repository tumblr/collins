require 'consolr/runners/runner'

module Consolr
  module Runners
    class Ipmitool < Runner
      def initialize config
        @ipmitool = if config.empty?
                      '/usr/bin/ipmitool'
                    else
                      config
                    end
      end

      def can_run? node
        not (node.ipmi.address.empty? or node.ipmi.username.empty? or node.ipmi.password.empty?)
      end

			def verify node
      	Net::Ping::External.new(node.ipmi.address).ping?
			end

      def console node
        cmd 'sol activate', node
      end

      def kick node
        cmd 'sol deactivate', node
      end

      def identify node
        cmd 'chassis identify', node
      end

      def sdr node
        cmd 'sdr elist all', node
      end

      def log_list node
        cmd 'sel list', node
      end

      def log_clear node
        cmd 'sel clear', node
      end

      def on node
        cmd 'power on', node
      end

      def off node
        cmd 'power off', node
      end

      def reboot node
        cmd 'power cycle', node
      end

      private
      def cmd action, node
        system("#{@ipmitool} -I lanplus -H #{node.ipmi.address} -U #{node.ipmi.username} -P #{node.ipmi.password} #{action}")
        return $?.exitstatus == 0 ? "SUCCESS" : "FAILED"
      end
    end
  end
end

