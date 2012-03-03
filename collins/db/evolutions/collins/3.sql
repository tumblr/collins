# --- New Asset Type

# --- !Ups

INSERT INTO asset_type VALUES (9, 'Configuration');

# --- !Downs

DELETE FROM asset_type WHERE id=9;
