create table if not exists delivery_orders (
    order_id varchar(64) primary key,
    customer_id varchar(64) not null,
    item_summary varchar(512) not null,
    subtotal decimal(10, 2) not null,
    total decimal(10, 2) not null,
    eta_label varchar(128) not null,
    payment_status varchar(64) not null,
    created_at timestamp not null
);
