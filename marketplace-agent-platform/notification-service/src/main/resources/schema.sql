create table if not exists notification_dispatch (
    dispatch_id varchar(128) primary key,
    case_id varchar(64) not null,
    outcome_type varchar(64) not null,
    outcome_status varchar(64) not null,
    settlement_reference varchar(128) not null unique,
    dispatch_status varchar(64) not null,
    delivery_status varchar(64) not null,
    participant_channel varchar(64),
    operator_channel varchar(64),
    participant_summary varchar(1000),
    operator_summary varchar(1000),
    summary varchar(1000) not null,
    created_at timestamp with time zone not null
);

alter table if exists notification_dispatch add column if not exists participant_channel varchar(64);
alter table if exists notification_dispatch add column if not exists operator_channel varchar(64);
alter table if exists notification_dispatch add column if not exists participant_summary varchar(1000);
alter table if exists notification_dispatch add column if not exists operator_summary varchar(1000);