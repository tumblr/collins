# --- Include new common server base fields

# --- !Ups

INSERT INTO asset_meta (name, priority, label, description) VALUES ('BASE_PRODUCT', 1, 'Base Product', 'Formal product model designation');
INSERT INTO asset_meta (name, priority, label, description) VALUES ('BASE_VENDOR', 1, 'Base Vendor', 'Who made your spiffy computer?');
INSERT INTO asset_meta (name, priority, label, description) VALUES ('BASE_DESCRIPTION', -1, 'Base Description', 'How does your computer introduce itself?');
INSERT INTO asset_meta (name, priority, label, description) VALUES ('BASE_SERIAL', -1, 'Base Serial', 'How does your computer identify itself?');
