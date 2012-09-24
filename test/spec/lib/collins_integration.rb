require 'yaml'
require 'mysql2'

module Output

  RED="\e[0;31m"
  BRED="\e[1;31m"
  BLUE="\e[0;34m"
  BBLUE="\e[1;34m"
  CYAN="\e[0;36m"
  BCYAN="\e[1;36m"
  ORANGE="\e[0;33m"
  NC="\e[0m" # No Color
  
  def Output.error message
    puts " #{RED}* #{message}#{NC}"
  end

  def Output.info message
    puts " #{CYAN}* #{message}#{NC}"
  end

  def Output.warning message
    puts " #{ORANGE}* #{message}#{NC}"
  end

end


# NOTE - this assumes that MySQL is running and that the collins_fixture
# database exists and is accessable to the db user
class CollinsIntegration
  include Output
  attr_reader :collinsClient

  def initialize(config_path, auto_prestart = true)
    if ! File.exists? config_path
      out.error "Cannot find yaml file #{config_path}"
      exit(1)
    end
    @config = YAML.load_file(config_path)
    if @config == nil
      out.error "Unable to load config #{config_path}"
      exit(1)
    end
    @collinsPath    = @config['collins_root']
    @mysqlCommand   = @config['mysql']['exec_path']
    @mysqlUser      = @config['mysql']['user']
    @mysqlPass      = @config['mysql']['password']
    @database       = @config['mysql']['database']


    @mysqlClient = Mysql2::Client.new(:host => "localhost", :username => @mysqlUser, :password => @mysqlPass, :database => @database)
    if @mysqlClient == nil
      Output.error "Error connecting to MySQL"
      exit(1)
    end

    checksum_file = "db_checksums.yaml"
    if ! File.exists? checksum_file
      Output.error "checksum file #{checksum_file} does not exist!"
      exit(1)
    end
    @dbChecksums = YAML.load_file(checksum_file)

    @collinsClient = Collins::Client.new @config['collins_client']

    if auto_prestart
      Output.info "Beginning prestart checks"
      preStart
    end

      
  end

  def preStart
    if !checkDatabase
      Output.warning "Detected dirty db, refreshing"
      reloadMySQL
      reloadSolr
    else
      Output.info "db is clean"
    end

    checkCollins true
  end

  #starts up collins server
  def startCollins
  end

  #shuts down collins server
  def stopCollins
  end

  def checkCollins output=false
    if @collinsClient.ping
      if output
        Output.info "Collins is running"
      end
    else
      if output
        Output.error "Collins not running"
      end
    end
  end

  #returns true if the db checksums match the config checksums, false otherwise
  def checkDatabase
    current_sums = getChecksums
    ok = true
    @dbChecksums.each do |key, expected|
      if expected != current_sums[key]
        ok = false
      end
    end
    ok
  end


  #loads the specified sql dump into database
  def reloadMySQL
    
    dump_file = "collins_fixture.dump"
    dump_zip = dump_file + ".gz"

    full_dump_path = @collinsPath + "/test/spec/#{dump_file}"
    d = File.dirname full_dump_path

    if ! File.exists? full_dump_path
      full_zip_path = full_dump_path + ".gz"
      if ! File.exists? full_zip_path
        system "cd #{d};git checkout #{dump_zip}"
        if ! File.exists? full_zip_path
          Output.error "Unable to find database dump #{dump_zip}"
          exit(1)
        end
      end
      system "cd #{d}; gunzip #{dump_zip}"
      if ! File.exists? dump_file
        Output.error "Error inflating dump file #{dump_zip}"
        exit(1)
      end
    end

    Output.info "Importing #{dump_file} to database #{@database}"
    upString = "-u #{@mysqlUser}"
    if @mysqlPass != nil
      upString += " -p#{@mysqlPass}"
    end
    out = system "cd #{d}; #{@mysqlCommand} #{upString} #{@database} < #{dump_file}"
    if ! out
      Output.error "Error with import: #{out}"
      exit(1)
    end
  end

  def reloadSolr
    Output.info "Reindexing Solr"
    @collinsClient.repopulate_solr!
  end

  #helper method for generating configs
  def outputChecksums
    puts "checksums:"
    getChecksums.each do |k,v|
      puts "  #{k}: #{v}"
    end
  end

  #returns a hash of TABLE_NAME => checksum
  def getChecksums
    checksums = {}
    results = @mysqlClient.query("show tables;")
    col = results.fields[0]
    results.each do |row|
      table = row[col]
      r = @mysqlClient.query("CHECKSUM TABLE #{table}").first['Checksum']
      checksums[table] = r
    end
    checksums
  end

end

