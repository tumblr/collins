# -- Deprecate old address related meta fields

# --- !Ups

UPDATE asset_meta SET priority = -1 WHERE id=33;

# --- !Downs

UPDATE asset_meta SET priority = 0 WHERE id=33;

