# --- Add GPU information to asset_meta

# --- !Ups

INSERT INTO asset_meta (name, priority, label, description) VALUES ('GPU_COUNT', -1, 'GPU Count', 'Number of physical GPUs in asset');
INSERT INTO asset_meta (name, priority, label, description) VALUES ('GPU_DESCRIPTION', -1, 'GPU Description', 'GPU description, vendor labels');

# --- !Downs

DELETE FROM asset_meta WHERE name ='GPU_COUNT'
DELETE FROM asset_meta WHERE name ='GPU_DESCRIPTION'
