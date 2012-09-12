# -- asset state schema

# --- !Ups

CREATE TABLE state (
  id                            INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
  status                        INTEGER NOT NULL DEFAULT 0,
  name                          VARCHAR(32) NOT NULL DEFAULT '' UNIQUE,
  label                         VARCHAR(32) NOT NULL DEFAULT '',
  description                   VARCHAR(255) NOT NULL DEFAULT ''
);
CREATE INDEX state_status_idx ON state(status);

ALTER TABLE asset ADD COLUMN state INTEGER NOT NULL DEFAULT 0;
CREATE INDEX asset_state_idx ON asset (state);

INSERT INTO state VALUES(1, 0, 'NEW', 'New', 'A service in this state is inactive. It does minimal work and consumes minimal resources.');
INSERT INTO state VALUES(2, 0, 'STARTING', 'Starting', 'A service in this state is transitioning to Running.');
INSERT INTO state VALUES(3, 0, 'RUNNING', 'Running', 'A service in this state is operational.');
INSERT INTO state VALUES(4, 0, 'STOPPING', 'Stopping', 'A service in this state is transitioning to Terminated.');
INSERT INTO state VALUES(5, 0, 'TERMINATED', 'Terminated', 'A service in this state has completed execution normally. It does minimal work and consumes minimal resources.');
INSERT INTO state VALUES(6, 0, 'FAILED', 'Failed', 'A service in this state has encountered a problem and may not be operational. It cannot be started nor stopped.');
INSERT INTO state VALUES(7, 5, 'RELOCATION', 'Relocation', 'An asset is being physically relocated.');
INSERT INTO state VALUES(8, 5, 'IPMI_PROBLEM', 'IPMI Problem', 'An asset is experiencing IPMI issues and needs to be examined. It needs investigation.');
INSERT INTO state VALUES(9, 5, 'HARDWARE_PROBLEM', 'Hardware Problem', 'An asset is experiencing a non-IPMI issue and needs to be examined. It needs investigation.');
INSERT INTO state VALUES(10, 5, 'NETWORK_PROBLEM', 'Network Problem', 'An asset is experiencing a network problem that may or may not be hardware related. It needs investigation.');
INSERT INTO state VALUES(11, 5, 'HARDWARE_UPGRADE', 'Hardware Upgrade', 'An asset is in need or in process of having hardware upgraded.');
INSERT INTO state VALUES(12, 5, 'HW_TESTING', 'Hardware Testing', 'Performing some testing that requires putting the asset into a maintenance state.');
INSERT INTO state VALUES(13, 5, 'MAINT_NOOP', 'Maintenance NOOP', 'Doing nothing, bouncing this through maintenance for my own selfish reasons.');

# --- !Downs

ALTER TABLE asset DROP INDEX asset_state_idx;
ALTER TABLE asset DROP COLUMN state;
DELETE FROM state;
DROP TABLE IF EXISTS state;
