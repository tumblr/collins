# --- New LOCATION asset meta

# --- !Ups

INSERT INTO asset_meta VALUES (34, 'LOCATION', -1, 'Location', 'URL for data-center remote access');

# --- !Downs

DELETE FROM asset_meta WHERE id=34;
