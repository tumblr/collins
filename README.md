collins
=======

groovy kind of love

This is the source for github page for collins.

You can see it in action here:

http://tumblr.github.io/collins/

Learn more about github pages here:

https://help.github.com/categories/20/articles

Specifically, this page uses Jekyll, and you can learn more about how that works here:

https://help.github.com/articles/using-jekyll-with-pages

## Setup

```
$ bundle install
```

## Build

Builds the site with jekyll:
```
$ bundle exec rake
Configuration file: /Users/gabe/code/collins/_config.yml
            Source: /Users/gabe/code/collins
       Destination: /Users/gabe/code/collins/_site
 Incremental build: disabled. Enable with --incremental
      Generating...
                    done in 14.703 seconds.
 Auto-regeneration: disabled. Use --watch to enable.
```

To clean all the generated jekyll files:
```
$ bundle exec rake clean
```

## Run locally

Runs local instance of gh-pages on `localhost:4000`
```
$ bundle exec rake serve
 Auto-regeneration: disabled. Use --watch to enable.
Configuration file: /Users/gabe/code/collins/_config.yml
Configuration file: /Users/gabe/code/collins/_config.yml
            Source: /Users/gabe/code/collins
       Destination: /Users/gabe/code/collins/_site
 Incremental build: disabled. Enable with --incremental
      Generating...
                    done in 14.102 seconds.
 Auto-regeneration: enabled for '/Users/gabe/code/collins'
Configuration file: /Users/gabe/code/collins/_config.yml
    Server address: http://127.0.0.1:4000/
  Server running... press ctrl-c to stop.
```
