# -- Initial schema

# --- !Ups

create table status (
  id                            integer,
  name                          varchar(16) not null,
  description                   varchar(255) not null,
  constraint uniq_status_name unique (name),
  constraint pk_status primary key (id))
;
create table asset_type (
  id                            integer,
  name                          varchar(255) not null,
  constraint uniq_asset_type_name unique (name),
  constraint pk_id primary key (id))
;
create table asset (
  id                            bigint not null,
  secondary_id                  varchar(255) not null,
  status                        integer not null,
  asset_type                    integer not null,
  created                       timestamp not null default CURRENT_TIMESTAMP,
  updated                       timestamp,
  deleted                       timestamp,
  constraint uniq_sec_id unique (secondary_id),
  constraint fk_status foreign key (status) references status (id),
  constraint fk_type foreign key (asset_type) references asset_type (id),
  constraint pk_asset primary key (id))
;
create table asset_meta (
  id                            integer not null,
  name                          varchar(255) not null,
  priority                      integer not null default -1,
  label                         varchar(255) not null,
  description                   varchar(255) not null,
  constraint uniq_meta_name unique (name),
  constraint pk_type primary key(id))
;
create index assetMeta_display on asset_meta (priority);
create table asset_meta_value (
  asset_id                      bigint not null,
  asset_meta_id                 integer not null,
  value                         varchar(2147483646) not null,
  constraint fk_assetMeta_asset_1 foreign key (asset_id) references asset (id),
  constraint fk_assetMeta_asset_2 foreign key (asset_meta_id) references asset_meta(id))
;
create index assetMeta_name_value on asset_meta_value (value);

create sequence asset_seq start with 1000;
create sequence asset_meta_seq start with 1000;
create sequence asset_type_seq start with 10;
create sequence status_seq start with 5;

# --- !Downs

SET REFERENTIAL_INTEGRITY FALSE;
drop table if exists asset_meta_value;
drop table if exists asset_meta;
drop table if exists asset;
drop table if exists asset_type;
drop table if exists status;
SET REFERENTIAL_INTEGRITY TRUE;
drop sequence if exists asset_seq;
drop sequence if exists asset_meta_seq;
drop sequence if exists asset_type_seq;
drop sequence if exists status_seq;
