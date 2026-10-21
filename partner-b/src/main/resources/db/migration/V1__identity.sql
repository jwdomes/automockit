create table identity (
    id              bigserial primary key,
    first_name      text not null,
    last_name       text not null,
    birth_date      date not null,
    p_number        text,
    document_number text
);

create index identity_p_number on identity (p_number);
create index identity_document_number on identity (document_number);
