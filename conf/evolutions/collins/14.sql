# --- Add GPU information to asset_meta

# --- !Ups

INSERT INTO asset_meta (name, priority, label, description) VALUES ('GPU_PRODUCT', -1, 'GPU Product', 'GPU product (description of GPU)');
INSERT INTO asset_meta (name, priority, label, description) VALUES ('GPU_VENDOR', -1, 'GPU Vendor', 'GPU vendor');

# --- !Downs

DELETE FROM asset_meta WHERE name ='GPU_VENDOR'
DELETE FROM asset_meta WHERE name ='GPU_PRODUCT'
