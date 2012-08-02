shared_examples "flexible initializer" do |values|

  let(:initializer) { described_class }

  context "#initialize" do
    it "from a symbolic hash" do
      vals = values.inject({}) do |res, (k,v)|
        res[k.to_sym] = v
        res
      end
      klass = initializer.new vals
      values.map do |k,v|
        klass.send(k.downcase.to_sym).should == v
      end
    end
    it "from a string hash" do
      vals = values.inject({}) do |res, (k,v)|
        res[k.to_s] = v
        res
      end
      klass = initializer.new vals
      values.map do |k,v|
        klass.send(k.downcase.to_sym).should == v
      end
    end
    it "from a hash with inconsistent casing" do
      vals = values.inject({}) do |res, (k,v)|
        res[k.to_s.capitalize] = v
        res
      end
      klass = initializer.new vals
      values.map do |k,v|
        klass.send(k.downcase.to_sym).should == v
      end
    end
  end

end
