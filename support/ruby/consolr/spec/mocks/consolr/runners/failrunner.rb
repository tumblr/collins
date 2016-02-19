module Consolr
  module Runners
    class Failrunner < Runner
      def initialize config
        true
      end

      def can_run? node
        false
      end
    end
  end
end
