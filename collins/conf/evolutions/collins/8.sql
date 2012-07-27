# -- add simple typing to meta tags

# --- !Ups

ALTER TABLE asset_meta ADD value_type INT(4) NOT NULL DEFAULT 1;

# --- !Downs

ALTER TABLE asset_meta DROP value_type;
