# -- Deprecate old address related meta fields

# --- !Ups

UPDATE asset_meta SET priority = -1 WHERE id=35;

# --- !Downs

UPDATE asset_meta SET priority = 0 WHERE id=35;

