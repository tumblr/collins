# --- Include collins role/pool fields

# --- !Ups

INSERT INTO asset_meta (name, priority, label, description) VALUES ('HOSTNAME', 0, 'Hostname', 'Hostname of asset');
INSERT INTO asset_meta (name, priority, label, description) VALUES ('NODECLASS', 1, 'Nodeclass', 'Nodeclass of asset');
INSERT INTO asset_meta (name, priority, label, description) VALUES ('POOL', 2, 'Pool', 'Pool');
INSERT INTO asset_meta (name, priority, label, description) VALUES ('PRIMARY_ROLE', 3, 'Primary Role', 'Primary role of host or asset');
INSERT INTO asset_meta (name, priority, label, description) VALUES ('SECONDARY_ROLE', 4, 'Secondary Role', 'Secondary role of host or asset');
