# --- Include created by field in asset log

# --- !Ups

ALTER TABLE asset_log ADD COLUMN created_by CHAR(256) NOT NULL DEFAULT '';

# --- !Downs

ALTER TABLE asset_log drop COLUMN created_by;
