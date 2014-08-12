require 'terminal-table'
require 'collins_shell/util/printer_util'

module CollinsShell

  class LogPrinter

    include CollinsShell::PrinterUtil

    attr_reader :asset_tag, :logs, :options, :table, :table_width

    def initialize asset_or_tag, logs
      @asset_tag = Collins::Util.get_asset_or_tag(asset_or_tag).tag
      if logs.is_a?(Hash) then
        @logs = logs.delete(:logs) || []
        @options = logs
      else
        @logs = logs
        @options = {}
      end
      @table_width = 0
      @table = Terminal::Table.new(:title => "Logs for #{asset_tag}")
    end

    def render _logs = []
      setup_table
      setup_header
      need_separator, formatted_logs = format_logs _logs
      add_logs_to_table need_separator, formatted_logs
      render!
    end
    alias :to_s :render

    protected
    # Add the specified logs to the table instance, using a separator if required
    def add_logs_to_table need_separator, logs
      last_log_idx = logs.length - 1
      logs.each_with_index do |row, index|
        table << row
        # Don't print a separator if we don't have a multi-row message
        if index != last_log_idx && need_separator then
          table << :separator
        end
      end
    end

    # Format logs appropriately for table layout
    def format_logs _logs
      have_multi_row = streaming? # either false (default), or true based on streaming
      logs_to_render = if not _logs.empty? then _logs else logs end
      logs_formatted = logs_to_render.map do |log|
        entry = format_log_entry log
        have_multi_row = true unless entry.find_index{|e| e.to_s.include?("\n")}.nil?
        entry
      end
      [have_multi_row, logs_formatted]
    end

    # We force column values to pad so terminal-table doesn't try and be smart
    def format_column value, width, alignment
      value_s = value.to_s.strip
      if value_s.size > width then
        alignment = :left # force to left is we wrap
        value_s = wrap_text(value_s, width).strip
      end
      case alignment
      when :left
        value_s.ljust(width)
      when :right
        value_s.rjust(width)
      else
        value_s.center(width)
      end
    end

    def format_log_entry log
      res = []
      if streaming? then
        res = [
          format_column(format_datetime(log.CREATED), created_col_width, :center),
          format_column(log.TYPE, severity_col_width, :center),
          format_column(log.SOURCE, source_col_width, :center),
          format_column(log.FORMAT, format_col_width, :center),
          format_column(log.MESSAGE, message_col_width, :left)
        ]
        res.unshift(format_column(log.ASSET_TAG, tag_col_width, :left)) if requires_tag?
      else
        res = [
          format_datetime(log.CREATED), log.TYPE, log.SOURCE, log.FORMAT, wrap_text(log.MESSAGE.strip).strip
        ]
        res.unshift(log.ASSET_TAG) if requires_tag?
      end
      res
    end

    # Grab the rendered table and reset the table instance
    def render!
      if displayed? then
        buffer = table.rows.map do |row|
          row.render + "\n" + Terminal::Table::Separator.new(table).render
        end.join("\n")
      else
        buffer = table.render
      end
      set_displayed
      @table = Terminal::Table.new
      buffer
    end

    # If needed setup table headings
    def setup_header
      if options.fetch(:header, true) and not displayed? then
        if streaming? then
          headings = [
            format_column("Created", created_col_width, :center),
            format_column("Severity", severity_col_width, :center),
            format_column("Source", source_col_width, :center),
            format_column("Format", format_col_width, :center),
            format_column("Message", message_col_width, :center)
          ]
          headings.unshift(format_column("Asset", tag_col_width, :center)) if requires_tag?
        else
          headings = [
            "Created", "Severity", "Source", "Format", "Message"
          ]
          headings.unshift("Asset") if requires_tag?
        end
        table.headings = headings
      end
    end

    # Setup table width according to options
    def setup_table
      # Only setup table style once. Either dynamic or based on width of 
      if streaming? and not displayed? then
        # If logs will be streaming, use the maximum width for the table
        term_width = dynamic_terminal_width
        @table_width = term_width
        table.style = {:width => table_width}
      elsif streaming? then
        table.style = {:width => table_width}
      end
    end

    def streaming?
      options.fetch(:streaming, false)
    end

    def displayed?
      @already_displayed
    end
    def set_displayed
      @already_displayed = true
    end

    # Require a tag if mode is all
    def requires_tag?
      @requires_tag ||= (options.fetch(:all_tag, nil).to_s.downcase == asset_tag.to_s.downcase)
    end

    def created_col_width
      19 # this is the formatted datetime
    end
    def severity_col_width
      13 # width of INFORMATIONAL
    end
    def source_col_width
      8 # width of INTERNAL
    end
    def format_col_width
      16 # width of application/json
    end
    def tag_col_width
      requires_tag? ? 24 : 0
    end

    # This needs to take into account all the styling and such to avoid terminal table throwing an
    # exception
    def message_col_width
      cells = requires_tag? ? 6 : 5
      dyn_width = (table.cell_spacing * cells) + table.style.border_y.length
      table_width - created_col_width - severity_col_width - source_col_width - format_col_width - tag_col_width - dyn_width
    end

  end # class LogPrinter

end # module CollinsShell
