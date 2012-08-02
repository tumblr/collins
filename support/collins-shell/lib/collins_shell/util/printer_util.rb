require 'highline'

module CollinsShell

  module PrinterUtil

    def format_datetime value
      if !value.is_a?(DateTime) then
        val = DateTime.parse(value)
      else
        val = value
      end
      val.strftime('%Y-%m-%d %H:%M:%S')
    end

    def wrap_text(txt, col = 80)
      txt.gsub(/(.{1,#{col}})( +|$\n?)|(.{1,#{col}})/, "\\1\\3\n") 
    end

    def dynamic_terminal_width
      highline_width = HighLine::SystemExtensions.terminal_size[1].to_i
      thor_width = Thor::Shell::Basic.new.terminal_width.to_i
      [highline_width,thor_width,80].max
    end

  end

end
