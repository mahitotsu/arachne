create table if not exists notification_dispatch (
    dispatch_id varchar(128) primary key,
    case_id varchar(64) not null,
    outcome_type varchar(64) not null,
    outcome_status varchar(64) not null,
    settlement_reference varchar(128) not null unique,
    dispatch_status varchar(64) not null,
    delivery_status varchar(64) not null,
    summary varchar(1000) not null,
    created_at timestamp with time zone not null
);