create table identity (
    id              bigserial primary key,
    first_name      text not null,
    last_name       text not null,
    birth_date      date not null,
    ssn             text,
    document_number text
);

create index identity_ssn on identity (ssn);
create index identity_document_number on identity (document_number);
