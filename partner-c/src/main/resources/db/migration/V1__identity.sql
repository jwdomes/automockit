create table identity (
    id         bigserial primary key,
    first_name text not null,
    last_name  text not null,
    birth_date date not null,
    ssn        text not null
);

create index identity_ssn on identity (ssn);
