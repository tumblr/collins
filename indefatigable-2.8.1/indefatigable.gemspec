GENDIR="target/gen-rb/"
TWITTER=Dir["lib/ruby/*.rb"]

Gem::Specification.new do |s|
  s.name = %q{indefatigable}
  s.version = "0.0.1"
  s.date = %q{2011-08-12}
  s.authors = ["Your Name"]
  s.summary = %q{indefatigable provides thrift client service bindings}
  s.homepage = %q{http://github.com/tumblr/indefatigable}
  s.licenses = ["Tumblr"]
  s.files = ["indefatigable_constants.rb","indefatigable_service.rb","indefatigable_types.rb"].map{|e| "#{GENDIR}#{e}"} + ["src/scripts/indefatigable-console"] + TWITTER
  s.require_paths = ["target/gen-rb","lib/ruby"]
  s.bindir = "src/scripts"
  s.executables = ["indefatigable-console"]
  s.add_dependency('thrift_client', '0.6.2')
  s.add_dependency('thrift', '0.6')
end
