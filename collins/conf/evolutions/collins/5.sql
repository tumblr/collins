# -- IP Address Schema

# --- !Ups

CREATE TABLE ip_addresses (
  id                            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  asset_id                      BIGINT NOT NULL,
  gateway                       BIGINT NOT NULL,
  address                       BIGINT NOT NULL UNIQUE,
  netmask                       BIGINT NOT NULL,
  CONSTRAINT fk_ip_addr_aid     FOREIGN KEY (asset_id) REFERENCES asset (id) ON DELETE CASCADE
);
CREATE INDEX ip_gateway_idx ON ip_addresses (gateway);
CREATE INDEX ip_netmask_idx ON ip_addresses (netmask);
CREATE INDEX ip_combo ON ip_addresses (asset_id, address);

# --- !Downs

DROP TABLE IF EXISTS ip_addresses;
