require 'net/ping'

module Consolr
  module Runners
    class Runner
      def can_run? node
        raise 'can_run? is not implemented for this runner'
      end

      def verify node
        raise 'verify is not implemented for this runner'
      end

      def console node
        raise 'console is not implemented for this runner'
      end

      def kick node
        raise 'kick is not implemented for this runner'
      end

      def identify node
        raise 'identify is not implemented for this runner'
      end

      def sdr node
        raise 'sdr is not implemented for this runner'
      end

      def log_list node
        raise 'log list is not implemented for this runner'
      end

      def log_clear node
        raise 'log clear is not implemented for this runner'
      end

      def on node
        raise 'power on is not implemented for this runner'
      end

      def off node
        raise 'power off is not implemented for this runner'
      end

      def soft_off node
        raise 'soft_off is not implemented for this runner'
      end

      def reboot node
        raise 'reboot is not implemented for this runner'
      end

      def soft_reboot node
        raise 'reboot_soft is not implemented for this runner'
      end

      def status node
        raise 'status is not implemented for this runner'
      end

      def sensors node
        raise 'sensors is not implemented for this runner'
      end
    end
  end
end
              
