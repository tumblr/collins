# --- Sample dataset

# --- !Ups

INSERT INTO status VALUES (1, 'New', 'Asset has been entered into the system');
INSERT INTO status VALUES (2, 'Unallocated', 'Asset has gone through intake');
INSERT INTO status VALUES (3, 'Allocated', 'Asset is in use or is ready for use');
INSERT INTO status VALUES (4, 'Cancelled', 'Asset is scheduled for decommissioning');
INSERT INTO status VALUES (5, 'Maintenance', 'Asset is scheduled for maintenance');
INSERT INTO status VALUES (6, 'Decommissioned', 'Asset is gone');
INSERT INTO status VALUES (7, 'Incomplete', 'Asset has not been finalized');

INSERT INTO asset_type VALUES (1, 'Server Node');
INSERT INTO asset_type VALUES (2, 'Server Chassis');
INSERT INTO asset_type VALUES (3, 'Rack');
INSERT INTO asset_type VALUES (4, 'Switch');
INSERT INTO asset_type VALUES (5, 'Router');
INSERT INTO asset_type VALUES (6, 'Power Circuit');
INSERT INTO asset_type VALUES (7, 'Power Strip');
INSERT INTO asset_type VALUES (8, 'Data Center');

INSERT INTO asset_meta VALUES (1, 'SERVICE_TAG', 1, 'Service Tag', 'Vendor supplied service tag');
INSERT INTO asset_meta VALUES (2, 'CHASSIS_TAG', 1, 'Chassis Tag', 'Tag for asset chassis');
INSERT INTO asset_meta VALUES (3, 'RACK_POSITION', 1, 'Rack Position', 'Position of asset in rack');
INSERT INTO asset_meta VALUES (4, 'POWER_PORT', 2, 'Power Port', 'Power port of asset');
INSERT INTO asset_meta VALUES (5, 'SWITCH_PORT', 2, 'Switch Port', 'Switch port that asset is connected to');

INSERT INTO asset_meta VALUES (6, 'CPU_COUNT', -1, 'CPU Count', 'Number of physical CPUs in asset');
INSERT INTO asset_meta VALUES (7, 'CPU_CORES', -1, 'CPU Cores', 'Number of cores per physical CPU');
INSERT INTO asset_meta VALUES (8, 'CPU_THREADS', -1, 'CPU Threads', 'Number of threads per CPU core');
INSERT INTO asset_meta VALUES (9, 'CPU_SPEED_GHZ', 3, 'CPU Speed', 'CPU Speed in GHz');
INSERT INTO asset_meta VALUES (10, 'CPU_DESCRIPTION', -1, 'CPU Description', 'CPU description, vendor labels');

INSERT INTO asset_meta VALUES (11, 'MEMORY_SIZE_BYTES', -1, 'Memory', 'Size of Memory Stick');
INSERT INTO asset_meta VALUES (12, 'MEMORY_DESCRIPTION', -1, 'Memory Description', 'Memory description, vendor label');
INSERT INTO asset_meta VALUES (13, 'MEMORY_SIZE_TOTAL', 4, 'Memory Total', 'Total amount of available memory in bytes');
INSERT INTO asset_meta VALUES (14, 'MEMORY_BANKS_TOTAL', -1, 'Memory Banks', 'Total number of memory banks');

INSERT INTO asset_meta VALUES (15, 'NIC_SPEED', 5, 'NIC Speed', 'Speed of nic, stored as bits per second');
INSERT INTO asset_meta VALUES (16, 'MAC_ADDRESS', 2, 'MAC Address', 'MAC Address of NIC');
INSERT INTO asset_meta VALUES (17, 'NIC_DESCRIPTION', -1, 'NIC Description', 'Vendor labels for NIC');

INSERT INTO asset_meta VALUES (18, 'DISK_SIZE_BYTES', -1, 'Disk Size', 'Disk size in bytes');
INSERT INTO asset_meta VALUES (19, 'DISK_TYPE', 6, 'Inferred disk type', 'Inferred disk type: SCSI, IDE or FLASH');
INSERT INTO asset_meta VALUES (20, 'DISK_DESCRIPTION', -1, 'Disk Description', 'Vendor labels for disk');
INSERT INTO asset_meta VALUES (21, 'DISK_STORAGE_TOTAL', 7, 'Total disk storage', 'Total amount of available storage');

INSERT INTO asset_meta VALUES (22, 'LLDP_INTERFACE_NAME', -1, 'LLDP Interface Name', 'Interface name reported by lldpctl');
INSERT INTO asset_meta VALUES (23, 'LLDP_CHASSIS_NAME', -1, 'LLDP Chassis Name', 'Chassis name reported by lldpctl');
INSERT INTO asset_meta VALUES (24, 'LLDP_CHASSIS_ID_TYPE', -1, 'LLDP Chassis ID Type', 'Chassis ID Type reported by lldpctl');
INSERT INTO asset_meta VALUES (25, 'LLDP_CHASSIS_ID_VALUE', -1, 'LLDP Chassis ID Value', 'Chassis ID Value reported by lldpctl');
INSERT INTO asset_meta VALUES (26, 'LLDP_CHASSIS_DESCRIPTION', -1, 'LLDP Chassis Description', 'Chassis Description reported by lldpctl');
INSERT INTO asset_meta VALUES (27, 'LLDP_PORT_ID_TYPE', -1, 'LLDP Port ID Type', 'Port ID Type reported by lldpctl');
INSERT INTO asset_meta VALUES (28, 'LLDP_PORT_ID_VALUE', -1, 'LLDP Port ID Value', 'Port ID Value reported by lldpctl');
INSERT INTO asset_meta VALUES (29, 'LLDP_PORT_DESCRIPTION', -1, 'LLDP Port Description', 'Port Description reported by lldpctl');
INSERT INTO asset_meta VALUES (30, 'LLDP_VLAN_ID', -1, 'LLDP VLAN ID', 'VLAN ID reported by lldpctl');
INSERT INTO asset_meta VALUES (31, 'LLDP_VLAN_NAME', -1, 'LLDP VLANE Name', 'VLAN name reported by lldpctl');
INSERT INTO asset_meta VALUES (32, 'INTERFACE_NAME', -1, 'Interface Name', 'Name of physical interface, e.g. eth0');
INSERT INTO asset_meta VALUES (33, 'INTERFACE_ADDRESS', 0, 'IP Address', 'Address on interface, e.g. 10.0.0.1');

INSERT INTO asset_meta VALUES (34, 'GPU_COUNT', -1, 'GPU Count', 'Number of physical GPUs in asset');
INSERT INTO asset_meta VALUES (35, 'GPU_DESCRIPTION', -1, 'GPU Description', 'GPU description, vendor labels');

INSERT INTO asset VALUES (1, 'tumblrtag1', 7, 1, CURRENT_TIMESTAMP, null, null);

-- gateway 10.0.0.1, ip address 10.0.0.2, netmask /19 = 255.255.224.0
INSERT INTO ipmi_info VALUES(1, 1, 'test-user', '', 167772161, 167772162, 4294959104);

INSERT INTO asset_meta_value VALUES(1, 1, 0, 'dell service tag 123');
INSERT INTO asset_meta_value VALUES(1, 2, 0, 'chassis tag abc');

INSERT INTO asset_log SET asset_id=1, message_type=6, message='Automatically created by database migration';

# --- !Downs

DELETE FROM asset_meta_value;
DELETE FROM asset_log;
DELETE FROM asset_meta;
DELETE FROM asset;
DELETE FROM asset_type;
DELETE FROM status;
