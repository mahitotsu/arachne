create table if not exists customer_accounts (
    customer_id varchar(64) primary key,
    login_id varchar(64) not null unique,
    password_hash varchar(255) not null,
    display_name varchar(120) not null,
    default_locale varchar(20) not null,
    scopes varchar(255) not null
);