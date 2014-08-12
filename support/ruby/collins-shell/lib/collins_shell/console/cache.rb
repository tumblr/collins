# This is a very basic shared cache class
module CollinsShell; module Console; module Cache
  @@_cache = {}

  def clear_cache
    @@_cache = {}
  end

  def cache_get_or_else key, &block
    if @@_cache[key].nil? then
      @@_cache[key] = block.call
    else
      @@_cache[key]
    end
  end

end; end; end
