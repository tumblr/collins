module CollinsNotify; module Version

  class << self
    def location= loc
      @location = loc
    end
    def location
      @location || File.absolute_path(File.join(File.dirname(__FILE__), '..', '..', 'VERSION'))
    end
    def to_s
      if location && File.exists?(location) then
        File.read(location)
      else
        "0.0.0-pre"
      end
    end
  end

end; end
