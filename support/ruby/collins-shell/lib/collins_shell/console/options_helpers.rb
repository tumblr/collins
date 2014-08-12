module CollinsShell; module Console; module OptionsHelpers

  module_function
  def pager_options opt
    opt.on :f, :flood, "Do not use a pager to view text longer than one screen"
  end

end; end; end
