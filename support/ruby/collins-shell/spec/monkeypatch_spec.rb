require 'rspec'
require_relative '../lib/collins_shell/monkeypatch'

describe Float do
  describe '#to_human_size' do
    {
      0.0 => '0 Bytes',
      -0.0 => '0 Bytes',
      # FIXME: floating point precision problems here
      Float::EPSILON => '256.00000000000000 Bytes',
      1.2 => '1.2 Bytes',
      12.3 => '12.3 Bytes',
      123.4 => '123.4 Bytes',
      1234.5 => '1.20556640625000 KB',
      12345.6 => '12.05625000000000 KB',
      123456.7 => '120.56318359375000 KB',
      1234567.8 => '1.17737560272217 MB',
      12345678.9 => '11.77375688552856 MB',
      123456789.0 => '117.73756885528564 MB',
      1234567890.1 => '1.14978094594553 GB',
      12345678901.2 => '11.49780945964158 GB',
      123456789012.3 => '114.97809459669516 GB',
      1234567890123.4 => '1.12283295504621 TB',
      12345678901234.5 => '11.22832955046260 TB',
      123456789012345.6 => '112.28329550462658 TB',
      1234567890123456.7 => '1.09651655766237 PB',
      12345678901234567.8 => '10.96516557662370 PB',
      123456789012345678.9 => '109.65165576623697 PB',
    }.each do |from, want|
      it "converts #{from} to #{want}" do
        expect(from.to_human_size).to eql(want)
      end
    end
  end
end

describe Integer do
  describe '#to_human_size' do
    {
      0 => '0 Bytes',
      1 => '1 Bytes',
      12 => '12 Bytes',
      123 => '123 Bytes',
      1234 => '1.00 KB',
      12345 => '12.00 KB',
      123456 => '120.00 KB',
      1234567 => '1.00 MB',
      12345678 => '11.00 MB',
      123456789 => '117.00 MB',
      1234567890 => '1.00 GB',
      12345678901 => '11.00 GB',
    }.each do |from, want|
      it "converts #{from} to #{want}" do
        expect(from.to_human_size).to eql(want)
      end
    end
  end
end
