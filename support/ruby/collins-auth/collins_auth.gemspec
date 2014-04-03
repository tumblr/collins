Gem::Specification.new do |s|
  s.name = "collins_auth"
  s.version = "0.0.5"
  s.date = "2013-11-22"
  s.summary = "Library to aid in getting an authenticated Collins::Client"
  s.description = "This is a library to make it easy to obtain an authenticated collins_client object. It manages ENV['COLLINS_CLIENT_CONFIG'], ~/.collins.yml, /etc/collins.yml, /var/db/collins.yml, and user interaction to determine the right configuration and authentication tokens."
  s.authors = ["Michael Benedict"]
  s.email = "benedict@tumblr.com"
  s.license = "Apache License 2.0"
  s.homepage = "https://github.com/tumblr/collins/tree/master/support/ruby"
  
  s.files = Dir["{lib}/*.rb", "*.md", "*.txt"]
  
  s.add_dependency 'collins_client', '~> 0.2', '>= 0.2.10'
  s.add_dependency 'highline', '~> 1.6', '>= 1.6.13'
end
