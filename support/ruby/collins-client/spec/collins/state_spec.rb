require 'spec_helper'

describe Collins::AssetState do
  context "Partial Asset - no State" do
    subject {
      asset = Collins::Asset.from_json(CollinsFixture.partial_asset_no_state(true))
      asset.state
    }

    it "is_a?(Collins::AssetState)" do
      subject.is_a?(Collins::AssetState).should be_true
    end

    it "#empty?" do
      subject.empty?.should be_true
    end

    it "#description" do
      subject.description.should be_empty
    end
    it "#label" do
      subject.description.should be_empty
    end
    it "#name" do
      subject.description.should be_empty
    end
  end

  context "Full Asset - With State" do
    subject {
      asset = Collins::Asset.from_json(CollinsFixture.full_asset_w_state(true))
      asset.state
    }

    it "is_a?(Collins::AssetState)" do
      subject.is_a?(Collins::AssetState).should be_true
    end

    it "#empty?" do
      subject.empty?.should be_false
    end

    it "#description" do
      subject.description.should_not be_empty
      subject.description.should === 'A service in this state is transitioning to Running.'
    end
    it "#label" do
      subject.label.should_not be_empty
      subject.label.should === 'Starting'
    end
    it "#name" do
      subject.name.should_not be_empty
      subject.name.should === 'STARTING'
    end
  end

end
