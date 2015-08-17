module MockCollinsMixin
  require 'collins_client'

  def test_assets
    Dir.glob('spec/fixtures/assets/*.yml').map do |f|
      YAML.load(File.read(f))
    end
  end

  def get_test_asset tag
    test_assets.select { |a| a.tag == tag }
  end

  def mock_collins
    allow_any_instance_of(Collins::Client).to receive(:find).
                                               with(Hash).
                                               and_return([])

    allow_any_instance_of(Collins::Client).to receive(:find).
                                              with(hash_including(:hostname => 'hostname-with-multiple-assets.dc.net')).
                                              and_return(['asset1', 'asset2'])


    ['safe-allocated-tag', 'safe-maintenance-tag', 'dangerous-allocated-tag', 'dangerous-maintenance-tag'].each do |tag|
      allow_any_instance_of(Collins::Client).to receive(:find).
                                                 with(hash_including(:tag => tag)).
                                                 and_return(get_test_asset(tag))
    end
  end

end
