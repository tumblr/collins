GENDIR="target/gen-rb/"
TWITTER=Dir["lib/ruby/*.rb"]

Gem::Specification.new do |s|
  s.name = %q{serx}
  s.version = "0.0.1"
  s.date = %q{2011-08-12}
  s.authors = ["Your Name"]
  s.summary = %q{serx provides thrift client service bindings}
  s.homepage = %q{http://github.com/tumblr/serx}
  s.licenses = ["Tumblr"]
  s.files = ["serx_constants.rb","serx_service.rb","serx_types.rb"].map{|e| "#{GENDIR}#{e}"} + ["src/scripts/serx-console"] + TWITTER
  s.require_paths = ["target/gen-rb","lib/ruby"]
  s.bindir = "src/scripts"
  s.executables = ["serx-console"]
  s.add_dependency('thrift_client', '0.6.2')
  s.add_dependency('thrift', '0.6')
end
