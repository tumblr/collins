# -- Alter IP Address Table

# --- !Ups

ALTER TABLE ip_addresses ADD COLUMN pool CHAR(32) NOT NULL DEFAULT '' AFTER netmask;
CREATE INDEX pool_idx ON ip_addresses (pool);

# --- !Downs

ALTER TABLE ip_addresses DROP INDEX pool_idx;
ALTER TABLE ip_addresses DROP COLUMN pool;
