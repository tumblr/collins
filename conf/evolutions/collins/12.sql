# --- Include collins role/pool fields

# --- !Ups

INSERT INTO asset_meta (name, priority, label, description) VALUES ('HOSTNAME', 0, 'Hostname', 'Hostname of asset');
INSERT INTO asset_meta (name, priority, label, description) VALUES ('NODECLASS', 0, 'Nodeclass', 'Nodeclass of asset');
INSERT INTO asset_meta (name, priority, label, description) VALUES ('POOL', 0, 'Pool', 'Groups related assets spanning multiple functional roles');
INSERT INTO asset_meta (name, priority, label, description) VALUES ('PRIMARY_ROLE', 0, 'Primary Role', 'Primary functional role of asset');
INSERT INTO asset_meta (name, priority, label, description) VALUES ('SECONDARY_ROLE', 0, 'Secondary Role', 'Secondary functional role of asset');
