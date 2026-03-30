create table if not exists escrow_cases (
    case_id varchar(64) primary key,
    hold_state varchar(64) not null,
    amount decimal(19, 2) not null,
    currency varchar(16) not null,
    prior_settlement_status varchar(64) not null,
    updated_at timestamp with time zone not null
);

create table if not exists escrow_settlement_audit (
    audit_id varchar(128) primary key,
    case_id varchar(64) not null,
    outcome_type varchar(64) not null,
    outcome_status varchar(64) not null,
    settled_at timestamp with time zone not null,
    settlement_reference varchar(128) not null,
    summary varchar(1000) not null,
    constraint fk_escrow_settlement_case
        foreign key (case_id) references escrow_cases(case_id)
);

create index if not exists idx_escrow_settlement_case on escrow_settlement_audit(case_id, settled_at);