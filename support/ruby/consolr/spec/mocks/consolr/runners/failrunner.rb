module Consolr
  module Runners
    class Testrunner < Runner
      def initialize
        true
      end

      def can_run? node
        false
      end
    end
  end
end
