# --- Sample dataset

# --- !Ups

insert into status values (1, 'New', 'Asset has been entered into the system');
insert into status values (2, 'Unallocated', 'Asset has gone through intake');
insert into status values (3, 'Allocated', 'Asset is in use or is ready for use');
insert into status values (4, 'Cancelled', 'Asset is scheduled for decommissioning');
insert into status values (5, 'Maintenace', 'Asset is scheduled for maintenance');
insert into status values (6, 'Decommissioned', 'Asset is gone');

insert into asset_type values (1, 'Server Nodes');
insert into asset_type values (2, 'Server Chassis');
insert into asset_type values (3, 'Rack');
insert into asset_type values (4, 'Switch');
insert into asset_type values (5, 'Router');
insert into asset_type values (6, 'Power Circuit');
insert into asset_type values (7, 'Power Strip');

insert into asset_meta values (1, 0, 'Service Tag', 'Vendor supplied service tag');
insert into asset_meta values (2, 1, 'Chassis Tag', 'Tag for asset chassis');
insert into asset_meta values (3, 2, 'IP Address', 'Primary IP address');
insert into asset_meta values (4, 2, 'IPMI Address', 'IPMI Address');
insert into asset_meta values (5, 3, 'Hostname', 'Hostname');
insert into asset_meta values (6, 2, 'MAC Address', 'MAC Address of primary interface');
insert into asset_meta values (7, 4, 'Rack Position', 'Position of asset in rack');
insert into asset_meta values (8, 5, 'Power Port', 'Power port of asset');
insert into asset_meta values (9, 5, 'Switch Port', 'Switch port that asset is connected to');
insert into asset_meta values (10, 6, 'Size', 'Hardware size');

insert into asset values (1, 'tumblrtag1', 1, 1, CURRENT_TIMESTAMP, null, null);

insert into asset_meta_value values(1, 1, 'asset tag 123');
insert into asset_meta_value values(1, 2, 'chassis tag abc');
insert into asset_meta_value values(1, 4, '10.0.0.1');
insert into asset_meta_value values(1, 5, 'test.tumblr.test');

# --- !Downs

delete from asset_meta_value;
delete from asset_meta;
delete from asset;
delete from asset_type;
delete from status;
