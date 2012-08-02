require 'collins/util'

module Collins

  class Profile

    include Collins::Util

    attr_accessor :options

    def initialize options = {}
      @options = symbolize_hash options, :downcase => true
    end

    def profile
      @options[:profile]
    end
    def profile?
      not profile.nil?
    end

    def label
      @options[:label]
    end
    def label?
      not label.nil?
    end

    def prefix
      @options[:prefix]
    end
    def prefix?
      not prefix.nil?
    end

    def suffix_allowed?
      @options[:suffix_allowed]
    end

    def primary_role
      @options[:primary_role]
    end
    def primary_role?
      not primary_role.nil?
    end

    def pool
      @options[:pool]
    end
    def pool?
      not pool.nil?
    end

    def secondary_role
      @options[:secondary_role]
    end
    def secondary_role?
      not secondary_role.nil?
    end

    def requires_primary_role?
      @options[:requires_primary_role]
    end
    def requires_pool?
      @options[:requires_pool]
    end
    def requires_secondary_role?
      @options[:requires_secondary_role]
    end

  end

end
