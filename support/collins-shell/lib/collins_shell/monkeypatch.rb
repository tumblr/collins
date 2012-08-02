class Numeric
  SIZE_ARRAY = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB']
end

class Float
  def to_human_size
    return "0 Bytes" if (self == 0)
    i = (Math.log(self) / Math.log(1024)).floor.to_i
    small = self / (1024 ** i)
    if i == 0 then
      "#{self} #{SIZE_ARRAY[i]}"
    else
      sprintf("%.14f %s", small, SIZE_ARRAY[i])
    end
  end
end

class Fixnum

  def to_human_size
    return "0 Bytes" if (self == 0)
    i = (Math.log(self) / Math.log(1024)).floor.to_i
    small = self / (1024 ** i)
    if i == 0 then
      "#{self} #{SIZE_ARRAY[i]}"
    else
      sprintf("%.2f %s", small, SIZE_ARRAY[i])
    end
  end

end

class String

  def is_disk_size?
    s = self.downcase
    s.include?("gb") or s.include?("mb") or s.include?("tb")
  end

  def to_bytes
    s = self.downcase
    size_h = ""
    multiplier = 0
    if s.include?("mb") then
      multiplier = (1024 ** 2)
      size_h = s.split('mb')[0]
    elsif s.include?("gb") then
      multiplier = (1024 ** 3)
      size_h = s.split('gb')[0]
    elsif s.include?("tb") then
      multiplier = (1024 ** 4)
      size_h = s.split('tb')[0]
    else
      raise Exception.new("Unknown size: #{s}")
    end
    (multiplier * size_h.to_f).floor.to_i
  end


end
