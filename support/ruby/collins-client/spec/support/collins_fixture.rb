require 'json'

module CollinsFixture
  extend self

  def no_such_asset json = false
    get_fixture_data 'no_such_asset.json', json
  end
  def bare_asset json = false
    get_fixture_data 'bare_asset.json', json
  end
  def full_asset json = false
    get_fixture_data 'full_asset.json', json
  end
  def delete_conflict json = false
    get_fixture_data 'delete_conflict.json', json
  end
  def status_response json = false
    get_fixture_data 'status_response.json', json
  end
  def status_response_false json = false
    get_fixture_data 'status_response_false.json', json
  end
  def full_asset_w_state json = false
    get_fixture_data 'full_asset_w_state.json', json
  end
  def partial_asset_no_state json = false
    get_fixture_data 'partial_asset_no_state.json', json
  end
  def basic_log json=false
    get_fixture_data 'basic_log.json', json
  end

  def data name
    File.read(fixture_file(name))
  end

  def fixture_file name
    File.join(File.dirname(__FILE__), '..', 'fixtures', name)
  end

  def get_fixture_data filename, json
    if json then
      JSON.parse(data(filename))
    else
      data(filename)
    end
  end

end

