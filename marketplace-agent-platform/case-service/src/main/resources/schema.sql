create table if not exists cases (
    case_id varchar(64) primary key,
    case_type varchar(64) not null,
    case_status varchar(64) not null,
    order_id varchar(64) not null,
    transaction_id varchar(64) not null,
    amount decimal(19, 2) not null,
    currency varchar(16) not null,
    current_recommendation varchar(64) not null,
    approval_required boolean not null,
    approval_status varchar(64) not null,
    requested_role varchar(64),
    requested_at timestamp with time zone,
    decision_at timestamp with time zone,
    decision_by varchar(128),
    approval_comment varchar(1000),
    outcome_type varchar(64),
    outcome_status varchar(64),
    settled_at timestamp with time zone,
    settlement_reference varchar(128),
    outcome_summary varchar(1000),
    shipment_evidence varchar(1000) not null,
    escrow_evidence varchar(1000) not null,
    risk_evidence varchar(1000) not null,
    policy_reference varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table if not exists case_activity (
    event_id varchar(64) primary key,
    case_id varchar(64) not null,
    event_timestamp timestamp with time zone not null,
    kind varchar(64) not null,
    source varchar(64) not null,
    message varchar(1000) not null,
    structured_payload varchar(2000),
    constraint fk_case_activity_case
        foreign key (case_id) references cases(case_id)
);

create index if not exists idx_case_activity_case_id on case_activity(case_id, event_timestamp);