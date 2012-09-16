require 'rubygems'
require 'rake'
require 'jekyll'

task :help do
  puts("rake -T      # See available rake tasks")
  puts("rake serve   # Serve up a local static site for testing")
end

task :serve do
  # This is how github serves it: https://help.github.com/articles/using-jekyll-with-pages
  `jekyll --pygments --no-lsi --safe --serve`
end

task :default => :help
