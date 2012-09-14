require 'yaml'
require 'mysql2'

# NOTE - this assumes that MySQL is running and that the collins_fixture
# database exists and is accessable to the db user
class CollinsIntegration
  

  def initialize(config_path)
    if ! File.exists? config_path
      puts "Cannot find yaml file #{config_path}"
      exit(1)
    end
    @config = YAML.load_file(config_path)
    if @config == nil
      puts "Unable to load config #{config_path}"
      exit(1)
    end
    @collinsPath    = @config['collins_root']
    @mysqlCommand   = @config['mysql']['exec_path']
    @mysqlUser      = @config['mysql']['user']
    @mysqlPass      = @config['mysql']['password']
    @database       = @config['mysql']['database']


    @mysqlClient = Mysql2::Client.new(:host => "localhost", :username => @mysqlUser, :password => @mysqlPass, :database => @database)
    if @mysqlClient == nil
      puts "Error connecting to MySQL"
      exit(1)
    end

    checkDatabase
  end

  #starts up collins server
  def startCollins
  end

  #shuts down collins server
  def stopCollins
  end

  #check that collins is running
  def collinsRunning?
  end

  def checkDatabase
    current_sums = getChecksums
    ok = true
    @config['checksums'].each do |key, expected|
      if expected != current_sums[key]
        ok = false
      end
    end
    if !ok
      puts "Detected dirty db, refreshing"
      reloadMySQL
    else
      puts "db is clean :)"
    end
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
          puts "Unable to find database dump #{dump_zip}"
          exit(1)
        end
      end
      system "cd #{d}; gunzip #{dump_zip}"
      if ! File.exists? dump_file
        puts "Error inflating dump file #{dump_zip}"
        exit(1)
      end
    end

    puts "Importing #{dump_file} to database #{@database}"
    upString = "-u #{@mysqlUser}"
    if @mysqlPass != nil
      upString += " -p#{@mysqlPass}"
    end
    out = system "cd #{d}; #{@mysqlCommand} #{upString} #{@database} < #{dump_file}"
    if ! out
      puts "Error with import: #{out}"
      exit(1)
    end
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



