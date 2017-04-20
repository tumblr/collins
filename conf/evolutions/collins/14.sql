# --- Add GPU information to asset_meta

# --- !Ups

INSERT INTO asset_meta VALUES (34, 'GPU_COUNT', -1, 'GPU Count', 'Number of physical GPUs in asset');
INSERT INTO asset_meta VALUES (35, 'GPU_DESCRIPTION', -1, 'GPU Description', 'GPU description, vendor labels');

# --- !Downs

DELETE FROM asset_meta WHERE id in (34,35);
