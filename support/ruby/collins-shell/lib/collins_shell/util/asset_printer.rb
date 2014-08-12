require 'terminal-table'
require 'collins_shell/util/printer_util'

module CollinsShell

  class AssetPrinter

    include CollinsShell::PrinterUtil

    attr_accessor :asset, :detailed, :table, :col_size, :thor, :separator, :logs, :color

    def initialize asset, thor, options = {}
      @asset = asset
      @table = Terminal::Table.new
      @thor = thor
      @separator = options[:separator]
      @detailed = options.fetch(:detailed, true)
      @col_size = 6
      @color = options.fetch(:color, true)
      @logs = options[:logs]
    end

    def render
      table.title = header_text("Asset #{asset.tag}")
      collect_asset_basics
      collect_disk_data if detailed
      collect_ip_data if detailed
      collect_ipmi_data if detailed
      collect_nic_data if detailed
      collect_mem_data if detailed
      collect_power_data if detailed
      collect_xtra_data
      collect_log_data
      table_s = table.render
      if separator then
        width = (0...@col_size).inject(0) do |result, idx|
          result + table.column_width(idx)
        end
        sep_s = separator * (1.13 * width.to_f).to_i
        table_s + "\n#{sep_s}\n"
      else
        table_s
      end
    end
    alias :to_s :render

    def collect_asset_basics
      header = [:tag,:status,:type,:created,:updated,:location]
      table << get_row(header, true, header.length)
      table << :separator
      table << get_row(header.map{|s| asset.send(s)}, false, header.length)
      if asset.state && !asset.state.empty? then
        state = asset.state
        state_headers = [
          title_text("State Name"),
          title_text("State Label"),
          {:value => title_text("State Description"), :colspan => 4}
        ]
        table << :separator
        table << state_headers
        table << :separator
        table << [state.name, state.label, {:value => state.description, :colspan => 4}]
      end
      @col_size = table.number_of_columns
    end

    def collect_disk_data
      disk_header = ["SIZE_HUMAN", "SIZE", "TYPE", "DESCRIPTION"]
      row_title "Disks"
      table << get_row(disk_header, true)
      table << :separator
      asset.disks.each do |disk|
        table << get_row(disk_header.map{|s| disk[s]})
      end
    end

    def collect_log_data
      return if (not logs or logs.empty?)
      row_title "Log Entries"
      headers = [
        title_text("Created"),
        title_text("Severity"),
        title_text("Source"),
        title_text("Format"),
        {:value => title_text("Message"), :colspan => 2}
      ]
      table << headers
      table << :separator
      have_multi_row = false
      logs_formatted = logs.map do |log|
        rewritten_msg = log.MESSAGE.strip
        message = wrap_text(rewritten_msg).strip
        if message != rewritten_msg then
          have_multi_row = true
        end
        [
          format_datetime(log.CREATED), log.TYPE, log.SOURCE, log.FORMAT,
          {:value => message, :colspan => 2}
        ]
      end
      last_log_idx = logs.length - 1
      logs_formatted.each_with_index do |row, index|
        table << row
        # Don't print a separator if we don't have a multi-row message
        if index != last_log_idx && have_multi_row then
          table << :separator
        end
      end
    end

    def collect_ip_data
      address_header = [:address,:gateway,:netmask,:pool,:is_private?,:is_public?]
      row_title "IP Addresses"
      table << get_row(address_header, true)
      table << :separator
      asset.addresses.each{|a| table << get_row(address_header.map{|s| a.send(s)})}
    end

    def collect_ipmi_data
      ipmi_header = [:address,:gateway,:netmask,:username,:password]
      row_title "IPMI Information"
      table << get_row(ipmi_header, true)
      table << :separator
      table << get_row(ipmi_header.map {|i| asset.ipmi.send(i)})
    end

    def collect_nic_data
      nic_header = ["SPEED_HUMAN", "MAC_ADDRESS", "DESCRIPTION"]
      row_title "Network Interfaces"
      table << get_row(nic_header, true)
      table << :separator
      asset.nics.each {|nic| table << get_row(nic_header.map{|s| nic[s]})}
    end

    def collect_mem_data
      memory_header = ["BANK","SIZE","SIZE_HUMAN","DESCRIPTION"]
      row_title "Physical Memory"
      table << get_row(memory_header, true)
      table << :separator
      memory_total = 0
      asset.memory.each do |mem|
        memory_total += mem["SIZE"].to_i
        if mem["SIZE"].to_i != 0 then
          table << get_row(memory_header.map{|s| mem[s]})
        end
      end
      table << :separator
      table << [title_text("TOTAL SIZE (Bytes)"), {:value => memory_total.to_s, :colspan => 5}]
      table << [title_text("TOTAL SIZE (Human)"), {:value => memory_total.to_human_size, :colspan => 5}]
    end

    def collect_power_data
      power_header = [:type,:key,:value,:label]
      row_title "Power Information"
      table << get_row(power_header, true)
      table << :separator
      asset.power.each do |power|
        power.units.each do |unit|
          table << get_row(power_header.map {|key| unit.send(key)})
        end
      end
    end

    def format_text key, value
      key_s = key.to_s.downcase
      if key_s.start_with?("json_") or key_s.end_with?("_json") then
        begin
          JSON.parse(value, :symbolize_names => true).to_s
        rescue Exception => e
          value
        end
      else
        value
      end
    end

    def collect_xtra_data
      return unless (asset.extras and asset.extras["ATTRIBS"] && asset.extras["ATTRIBS"]["0"])
      row_title "Extra Attributes"
      attribs = []
      attribs_unsorted = asset.extras["ATTRIBS"]["0"]
      attribs_sorted = attribs_unsorted.keys.sort.inject({}) do |result,key|
        result.update(key => attribs_unsorted[key])
      end
      attribs_sorted.each do |key,value|
        attribs << title_text(key)
        attribs << format_text(key, value)
        if attribs.length == 6 then
          table << attribs
          attribs = []
        end
        if CollinsShell::Util::SIZABLE_ATTRIBUTES.include?(key.downcase.to_sym) then
          attribs << title_text("#{key} (Human)")
          attribs << value.to_f.to_human_size
        end
        if attribs.length == 6 then
          table << attribs
          attribs = []
        end
      end
      if attribs.length != 0 then
        until attribs.length == 6
          attribs << " "
        end
        table << attribs
      end
    end

    def header_text txt
      if @color then
        thor.set_color(txt, :bold, :magenta)
      else
        txt
      end
    end
    def title_text txt
      if @color then
        thor.set_color(txt, :bold, :white)
      else
        txt
      end
    end
    def regular_text txt
      txt
    end

    def get_row row_data, header = false, cols = 0
      num_cols = @col_size
      row_length = row_data.length
      last_index = row_length - 1
      average_size = (num_cols.to_f / row_length.to_f)
      est_size = (num_cols / row_length)
      est_is_actual = (average_size == est_size)
      used_cols = 0
      row = []
      row_data.each_with_index do |col,index|
        if est_is_actual then
          size_per_col = est_size
        elsif index == 0 then
          size_per_col = 1
        elsif average_size > (used_cols.to_f/index.to_f) then
          size_per_col = 2
        else
          size_per_col = 1
        end
        if col.is_a?(DateTime) then
          col = format_datetime(col)
        end
        used_cols += size_per_col
        alignment = if size_per_col > 1 then :center else :left end
        value = if header then title_text(col) else regular_text(col) end
        row << {:value => value, :alignment => alignment, :colspan => size_per_col}
      end
      row
    end

    def row_title txt
      table << :separator
      table << [{:value => header_text(txt), :alignment => :center, :colspan => @col_size}]
      table << :separator
    end

  end

end
