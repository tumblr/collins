# -- asset type labels

# --- !Ups

ALTER TABLE asset_type ADD COLUMN label VARCHAR(255) NOT NULL DEFAULT '';
UPDATE asset_type SET name='SERVER_NODE', label='Server Node' WHERE id=1;
UPDATE asset_type SET name='SERVER_CHASSIS', label='Server Chassis' WHERE id=2;
UPDATE asset_type SET name='RACK', label='Rack' WHERE id=3;
UPDATE asset_type SET name='SWITCH', label='Switch' WHERE id=4;
UPDATE asset_type SET name='ROUTER', label='Router' WHERE id=5;
UPDATE asset_type SET name='POWER_CIRCUIT', label='Power Circuit' WHERE id=6;
UPDATE asset_type SET name='POWER_STRIP', label='Power Strip' WHERE id=7;
UPDATE asset_type SET name='DATA_CENTER', label='Data Center' WHERE id=8;
UPDATE asset_type SET name='CONFIGURATION', label='Configuration' WHERE id=9;

# --- !Downs

ALTER TABLE asset_type DROP COLUMN label;
UPDATE asset_type SET name='Server Node' WHERE id=1;
UPDATE asset_type SET name='Server Chassis' WHERE id=2;
UPDATE asset_type SET name='Rack' WHERE id=3;
UPDATE asset_type SET name='Switch' WHERE id=4;
UPDATE asset_type SET name='Router' WHERE id=5;
UPDATE asset_type SET name='Power Circuit' WHERE id=6;
UPDATE asset_type SET name='Power Strip' WHERE id=7;
UPDATE asset_type SET name='Data Center' WHERE id=8;
UPDATE asset_type SET name='Configuration' WHERE id=9;

