module Consolr
  module Runners
    class Testrunner < Runner
      def initialize
        true
      end

      def can_run? node
        true
      end

      def verify node
        true
      end

      def on node
        'SUCCESS'
      end

      def power node
        'SUCCESS'
      end
    end
  end
end
