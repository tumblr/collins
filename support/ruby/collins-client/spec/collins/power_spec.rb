require 'spec_helper'

describe Collins::Power do
  subject {
    asset = Collins::Asset.from_json(CollinsFixture.full_asset(true))
    asset.power
  }

  it_behaves_like "flexible initializer", :unit_id => 23

  context "#normalize_action" do
    it "Off" do |example| example.description.should have_power_action "powerOff" end
    it "PowerOff" do |example| example.description.should have_power_action "powerOff" end

    it "On" do |example| example.description.should have_power_action "powerOn" end
    it "PowerOn" do |example| example.description.should have_power_action "powerOn" end

    it "PowerSoft" do |example| example.description.should have_power_action "powerSoft" end

    it "Soft" do |example| example.description.should have_power_action "rebootSoft" end
    it "RebootSoft" do |example| example.description.should have_power_action "rebootSoft" end

    it "Hard" do |example| example.description.should have_power_action "rebootHard" end
    it "RebootHard" do |example| example.description.should have_power_action "rebootHard" end

    it "Status" do |example| example.description.should have_power_action "powerState" end
    it "PowerState" do |example| example.description.should have_power_action "powerState" end

    it "Verify" do |example| example.description.should have_power_action "verify" end
    it "Identify" do |example| example.description.should have_power_action "identify" end
  end

  it "#keys" do
    subject[0].keys.should include("POWER_PORT_A")
    subject[1].keys.should include("POWER_PORT_B")
  end

  it "#values" do
    subject[0].values.should include("Unknown SL 1")
    subject[1].values.should include("Unknown SL 2")
  end

  it "#types" do
    subject[0].types.should include("POWER_PORT")
    subject[1].types.should include("POWER_PORT")
  end

  it "#labels" do
    subject[0].labels.should include("Plug Strip A")
    subject[1].labels.should include("Plug Strip B")
  end

  it "#positions" do
    subject[0].positions.should include(0)
    subject[1].positions.should include(0)
  end

end
