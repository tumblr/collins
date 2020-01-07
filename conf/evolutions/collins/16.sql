# --- Add logical name of disks to asset meta

# --- !Ups

INSERT INTO asset_meta (name, priority, label, description) VALUES ('DISK_LOGICAL_NAME', -1, 'Disk logical name', 'The logical name of the disk as reported by lshw');

# --- !Downs

DELETE FROM asset_meta WHERE name ='DISK_LOGICAL_NAME'
