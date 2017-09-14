# --- Add GPU information to asset_meta

# --- !Ups

INSERT INTO asset_meta (name, priority, label, description) VALUES ('BASE_MOTHERBOARD', -1, 'Base Motherboard', 'Motherboard product name');
INSERT INTO asset_meta (name, priority, label, description) VALUES ('BASE_FIRMWARE', -1, 'Base Firmware', 'The motherboard firmware version');
INSERT INTO asset_meta (name, priority, label, description) VALUES ('BASE_FIRMWAREDATE', -1, 'Base Firmware Date', 'The date the motherboard firmware was released');

# --- !Downs

DELETE FROM asset_meta WHERE name ='BASE_MOTHERBOARD'
DELETE FROM asset_meta WHERE name ='BASE_FIRMWARE'
DELETE FROM asset_meta WHERE name ='BASE_FIRMWAREDATE'
