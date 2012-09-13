require 'yaml'

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

    @upString = "-u #{@mysqlUser}"
    if @mysqlPass != nil
      @upString += " -p#{@mysqlPass}"
    end

    reloadMySQL
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

  #loads the specified sql dump into database
  def reloadMySQL
    
    dump_file = "collins_fixture.dump.gz"
    full_dump_path = @collinsPath + "/test/spec/#{dump_file}"
    d = File.dirname full_dump_path

    if ! File.exists? full_dump_path
      system "cd #{d};git checkout #{dump_file}"
      if ! File.exists? full_dump_path
        puts "Unable to find database dump #{dump_file}"
        exit(1)
      end
    end
    system "cd #{d}; gunzip #{dump_file}"
    inflated_dump = File.basename dump_file, ".gz"
    if ! File.exists? inflated_dump
      puts "Error inflating dump file #{dump_file}"
      exit(1)
    end
    puts "Importing #{dump_file} to database #{@database}"
    out = system "cd #{d}; #{@mysqlCommand} #{@upString} #{@database} < #{inflated_dump}"
    if ! out
      puts "Error with import: #{out}"
      exit(1)
    end
  end

  def verifyMySQL


  end

  #returns a hash of TABLE_NAME => checksum
  def getChecksums

  end

  def runMysqlQuery query
    cmd = "#{@mysqlCommand} #{@upString} #{@database} <<<MYSQLEOF\n#{@query}\nMYSQLEOF"
    system cmd
  end

end



