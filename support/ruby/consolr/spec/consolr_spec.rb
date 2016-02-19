require 'spec_helper'

describe Consolr::Console do

  before (:each) do
    @console = Consolr::Console.new
  end

  it 'fails if no hostname is provided' do
    expect { @console.start( {:hostname => nil}) }.to raise_error(SystemExit, /asset tag or hostname/)
  end

  it 'fails if both tag and hostname are passed' do
    expect { @console.start({:hostname => 'host.dc.net', :tag => '001234' }) }.to raise_error(SystemExit, /not both/)
  end

  it 'fails if tag cannot be found' do
    expect { @console.start({:tag => 'bogus_tag'}) }.to raise_error(SystemExit, /Found 0 assets/)
  end

  it 'fails if hostname cannot be found' do
    expect { @console.start({:hostname => 'bogus_hostname'}) }.to raise_error(SystemExit, /Found 0 assets/)
  end

  it 'fails if multiple hosts are found' do
    expect { @console.start({:hostname => 'hostname-with-multiple-assets.dc.net'}) }.to raise_error(SystemExit, /Found \d+ assets/)
  end

  it 'loads ipmitool as the default runner' do
    expect_any_instance_of(Consolr::Runners::Ipmitool).to receive(:initialize)
    @console.start({:tag => 'safe-allocated-tag', :on => true})
  end

  it 'prints an error when passed an unknown runner' do
    expect {
      @console.start({:tag => 'safe-allocated-tag', :on  => true, :runner => 'bogusrunner'})
    }.to raise_error SystemExit
  end

  it 'loads a custom runner when specified' do
    $LOAD_PATH << 'spec/mocks'
    require 'consolr/runners/testrunner'
    expect_any_instance_of(Consolr::Runners::Testrunner).to receive(:initialize)
    @console.start({:tag => 'safe-allocated-tag', :on => true, :runner => 'testrunner'})
  end

  it 'selects a runner that supports the node' do
    $LOAD_PATH << 'spec/mocks'
    require 'consolr/runners/testrunner'
    require 'consolr/runners/failrunner'
    old_config = ENV['CONSOLR_CONFIG']
    ENV['CONSOLR_CONFIG'] = '../../spec/configs/consolr_runners_rspec.yml'

    console = Consolr::Console.new
    expect_any_instance_of(Consolr::Runners::Failrunner).to receive(:can_run?).
      and_return(false)
    expect_any_instance_of(Consolr::Runners::Testrunner).to receive(:on)
    console.start({:tag => 'safe-allocated-tag', :on => true})

    ENV['CONSOLR_CONFIG'] = old_config
  end

  safe_boolean_actions = {:console => "--> Opening SOL session (type ~~. to quit)\nsol activate", :kick => 'sol deactivate', :identify => 'chassis identify', :sdr => 'sdr elist all', :on => 'power on'}
  dangerous_boolean_actions = {:off => 'power off', :reboot => 'power cycle'}

  describe 'safe allocated asset' do
    safe_allocated_tag = 'safe-allocated-tag'
    safe_boolean_actions.each do |action, response|
      it "does #{action}" do
        expect { @console.start({:tag => safe_allocated_tag, action => true}) }.to output("#{response}\nSUCCESS\n").to_stdout_from_any_process
      end
    end

    dangerous_boolean_actions.each do |action, response|
      it "refuses to do dangerous action #{action}" do
        expect { @console.start({:tag => safe_allocated_tag, action => true}) }.to raise_error(SystemExit, /Cannot run dangerous command/)
      end
      it "can force dangerous action #{action}" do
        expect { @console.start({:tag => safe_allocated_tag, action => true, :force => true}) }.to output("#{response}\nSUCCESS\n").to_stdout_from_any_process
      end
    end

    it 'does log list' do
      expect { @console.start({:tag => safe_allocated_tag, :log => 'list'}) }.to output("sel list\nSUCCESS\n").to_stdout_from_any_process
    end

    it 'does log clear' do
      expect { @console.start({:tag => safe_allocated_tag, :log => 'clear'}) }.to output("sel clear\nSUCCESS\n").to_stdout_from_any_process
    end
  end

  describe 'safe maintenance asset' do
    safe_maintenance_tag= 'safe-maintenance-tag'
    safe_boolean_actions.each do |action, response|
      it "does #{action}" do
        expect { @console.start({:tag => safe_maintenance_tag, action => true}) }.to output("#{response}\nSUCCESS\n").to_stdout_from_any_process
      end
    end

    dangerous_boolean_actions.each do |action, response|
      it "does dangerous action #{action}" do
        expect { @console.start({:tag => safe_maintenance_tag, action => true, :force => true}) }.to output("#{response}\nSUCCESS\n").to_stdout_from_any_process
      end
    end

    it 'does log list' do
      expect { @console.start({:tag => safe_maintenance_tag, :log => 'list'}) }.to output("sel list\nSUCCESS\n").to_stdout_from_any_process
    end

    it 'does log clear' do
      expect { @console.start({:tag => safe_maintenance_tag, :log => 'clear'}) }.to output("sel clear\nSUCCESS\n").to_stdout_from_any_process
    end
  end

  describe 'dangerous allocated asset' do
    dangerous_allocated_tag= 'dangerous-allocated-tag'
    safe_boolean_actions.each do |action, response|
      it "does #{action}" do
        expect { @console.start({:tag => dangerous_allocated_tag, action => true}) }.to output("#{response}\nSUCCESS\n").to_stdout_from_any_process
      end
    end

    dangerous_boolean_actions.each do |action, response|
      it "refuses to do dangerous action #{action}" do
        expect { @console.start({:tag => dangerous_allocated_tag, action => true}) }.to raise_error(SystemExit, /Asset .* is a crucial asset/)
      end
      it "refuses to do dangerous action #{action} with force" do
        expect { @console.start({:tag => dangerous_allocated_tag, action => true, :force => true}) }.to raise_error(SystemExit, /Asset .* is a crucial asset/)
      end
    end

    it 'does log list' do
      expect { @console.start({:tag => dangerous_allocated_tag, :log => 'list'}) }.to output("sel list\nSUCCESS\n").to_stdout_from_any_process
    end

    it 'does log clear' do
      expect { @console.start({:tag => dangerous_allocated_tag, :log => 'clear'}) }.to output("sel clear\nSUCCESS\n").to_stdout_from_any_process
    end
  end

  describe 'dangerous maintenance asset' do
    dangerous_maintenance_tag= 'dangerous-maintenance-tag'
    safe_boolean_actions.each do |action, response|
      it "does #{action}" do
        expect { @console.start({:tag => dangerous_maintenance_tag, action => true}) }.to output("#{response}\nSUCCESS\n").to_stdout_from_any_process
      end
    end

    dangerous_boolean_actions.each do |action, response|
      it "refuses to do dangerous action #{action}" do
        expect { @console.start({:tag => dangerous_maintenance_tag, action => true}) }.to raise_error(SystemExit, /Asset .* is a crucial asset/)
      end
      it "refuses to do dangerous action #{action} with force" do
        expect { @console.start({:tag => dangerous_maintenance_tag, action => true, :force=> true}) }.to raise_error(SystemExit, /Asset .* is a crucial asset/)
      end

    end

    it 'does log list' do
      expect { @console.start({:tag => dangerous_maintenance_tag, :log => 'list'}) }.to output("sel list\nSUCCESS\n").to_stdout_from_any_process
    end

    it 'does log clear' do
      expect { @console.start({:tag => dangerous_maintenance_tag, :log => 'clear'}) }.to output("sel clear\nSUCCESS\n").to_stdout_from_any_process
    end
  end
end
