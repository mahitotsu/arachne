create table if not exists risk_reviews (
    case_id varchar(64) primary key,
    order_id varchar(64) not null,
    operator_role varchar(64) not null,
    indicator_summary varchar(1000) not null,
    manual_review_required boolean not null,
    policy_flags varchar(255) not null,
    summary varchar(1000) not null,
    updated_at timestamp with time zone not null
);