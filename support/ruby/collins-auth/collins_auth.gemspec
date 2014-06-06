Gem::Specification.new do |s|
  s.name = "collins_auth"
  s.version = "0.1.1"
  s.date = "2013-11-22"
  s.summary = "Library to aid in getting an authenticated Collins::Client"
  s.description = "This is a library to make it easy to obtain an authenticated collins_client object. It attempts to load credentials from the following yaml files ENV['COLLINS_CLIENT_CONFIG'], ~/.collins.yml, ./.collins.yml, /etc/collins.yml, /var/db/collins.yml and supports user input."
  s.authors = ["Michael Benedict"]
  s.email = "benedict@tumblr.com"
  s.license = "Apache License 2.0"
  s.homepage = "https://github.com/tumblr/collins/tree/master/support/ruby/collins-auth"
  
  s.files = Dir["{lib}/*.rb", "*.md", "*.txt"]
  
  s.add_dependency 'collins_client'
  s.add_dependency 'highline'
end
