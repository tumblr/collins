# --- Sample dataset

# --- !Ups

INSERT INTO status VALUES (1, 'New', 'Asset has been entered into the system');
INSERT INTO status VALUES (2, 'Unallocated', 'Asset has gone through intake');
INSERT INTO status VALUES (3, 'Allocated', 'Asset is in use or is ready for use');
INSERT INTO status VALUES (4, 'Cancelled', 'Asset is scheduled for decommissioning');
INSERT INTO status VALUES (5, 'Maintenace', 'Asset is scheduled for maintenance');
INSERT INTO status VALUES (6, 'Decommissioned', 'Asset is gone');
INSERT INTO status VALUES (7, 'Incomplete', 'Asset has not been finalized');

INSERT INTO asset_type VALUES (1, 'Server Node');
INSERT INTO asset_type VALUES (2, 'Server Chassis');
INSERT INTO asset_type VALUES (3, 'Rack');
INSERT INTO asset_type VALUES (4, 'Switch');
INSERT INTO asset_type VALUES (5, 'Router');
INSERT INTO asset_type VALUES (6, 'Power Circuit');
INSERT INTO asset_type VALUES (7, 'Power Strip');

INSERT INTO asset_meta VALUES (1, 'SERVICE_TAG', 0, 'Service Tag', 'Vendor supplied service tag');
INSERT INTO asset_meta VALUES (2, 'CHASSIS_TAG', 0, 'Chassis Tag', 'Tag for asset chassis');
INSERT INTO asset_meta VALUES (3, 'IP_ADDRESS', 1, 'IP Address', 'Primary IP address');
INSERT INTO asset_meta VALUES (4, 'IPMI_ADDRESS', 1, 'IPMI Address', 'IPMI Address');
INSERT INTO asset_meta VALUES (5, 'HOSTNAME', 2, 'Hostname', 'Hostname');
INSERT INTO asset_meta VALUES (6, 'MAC_ADDRESS', 1, 'MAC Address', 'MAC Address of primary interface');
INSERT INTO asset_meta VALUES (7, 'RACK_POSITION', 3, 'Rack Position', 'Position of asset in rack');
INSERT INTO asset_meta VALUES (8, 'POWER_PORT', 4, 'Power Port', 'Power port of asset');
INSERT INTO asset_meta VALUES (9, 'SWITCH_PORT', 4, 'Switch Port', 'Switch port that asset is connected to');
INSERT INTO asset_meta VALUES (10, 'IPMI_CREDENTIALS', -1, 'IPMI Credentials', 'IPMI Credentials for host');

INSERT INTO asset VALUES (1, 'tumblrtag1', 1, 1, CURRENT_TIMESTAMP, null, null);

INSERT INTO asset_meta_value VALUES(1, 1, 'asset tag 123');
INSERT INTO asset_meta_value VALUES(1, 2, 'chassis tag abc');
INSERT INTO asset_meta_value VALUES(1, 4, '10.0.0.1');
INSERT INTO asset_meta_value VALUES(1, 5, 'test.tumblr.test');

# --- !Downs

DELETE FROM asset_meta_value;
DELETE FROM asset_meta;
DELETE FROM asset;
DELETE FROM asset_type;
DELETE FROM status;
