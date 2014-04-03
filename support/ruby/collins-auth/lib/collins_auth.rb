require 'collins_client'
require 'highline/import'
require 'yaml'
require 'socket'

module Collins
  module Authenticator
    def self.setup_client(options = nil)
      Collins::Client.new load_config(options)
    end

    def self.load_config options = {prompt: false}
      conf = (read_config || options) unless options[:prompt] == :only
      
      # check if we have all that we expect
      if [:username, :password, :host].all? {|key| conf.keys.include? key}
        return conf
      end
      
      # Something is missing. Can we prompt for it?
      if prompt
        conf.merge(prompt_creds(conf))
      else
        raise "could not load any valid configuration."
      end
      
      conf
    end
    
    private
    def self.file2conf(file)
      if file and File.readable? file
        # YAML config has keys as strings but we want symbols
        YAML.load_file(file).reduce({}) do |hash, (key, value)|
          hash[begin key.to_sym rescue key end] = value
          hash
        end
      end
    end

    def self.prompt_creds(conf={})
      puts 'collins information:'
      conf[:username] = ask('username: ') {|user| user.default = conf[:username] || ENV['USER']}
      conf[:password] ||= ask('password: ') {|password| password.echo = false}
      conf[:host]     ||= ask('host: ') {|host| host.default = ['https://collins', get_domain].compact.join('.')}
      conf
    end
     
    def self.read_config 
      conf = [ENV['COLLINS_CLIENT_CONFIG'], File.join(ENV['HOME'], '.collins.yml'), '/etc/collins.yml', '/var/db/collins.yml'].compact.find do |config_file|
        File.readable? config_file and File.size(config_file) > 0
      end
      
      file2conf conf
    end    
    
    def self.get_domain
      hostname = Socket.gethostname.downcase.split('.')
      
      if hostname.length > 1
        hostname.shift
        hostname.join('.')
      end
    end
  end
end