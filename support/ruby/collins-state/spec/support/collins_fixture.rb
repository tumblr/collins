require 'json'

module CollinsFixture
  extend self

  def full_asset json = false
    get_fixture_data 'full_asset.json', json
  end
  def status_response json = false
    get_fixture_data 'status_response.json', json
  end
  def status_response_false json = false
    get_fixture_data 'status_response_false.json', json
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

