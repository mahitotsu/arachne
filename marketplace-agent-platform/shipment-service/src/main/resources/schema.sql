create table if not exists shipment_cases (
    case_id varchar(64) primary key,
    order_id varchar(64) not null,
    tracking_number varchar(64) not null,
    milestone_summary varchar(1000) not null,
    delivery_confidence varchar(32) not null,
    shipping_exception_summary varchar(1000) not null,
    updated_at timestamp with time zone not null
);