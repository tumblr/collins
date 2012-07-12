# -- add simple typing to meta tags

# --- !Ups

ALTER TABLE asset_meta ADD value_type VARCHAR(32) NOT NULL DEFAULT 'STRING';

# --- !Downs

ALTER TABLE asset_meta DROP value_type;
