# --- New Status Fields

# --- !Ups

INSERT INTO status VALUES (8, 'Provisioning', 'Asset is currently being provisioned');
INSERT INTO status VALUES (9, 'Provisioned', 'Asset is provisioned but not allocated');

# --- !Downs

DELETE FROM status WHERE id IN (8,9);
