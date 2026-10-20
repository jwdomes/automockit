insert into identity (first_name, last_name, birth_date, ssn, document_number) values
    ('Ada',       'Lovelace', '1815-12-10', '111223333', 'AB1234567'),
    ('Ada',       'Byron',    '1815-12-10', '111223333', 'XY0000001'), -- same SSN, other passport
    ('Augusta',   'King',     '1815-12-10', '999887777', 'AB1234567'), -- same passport, other SSN
    ('Grace',     'Hopper',   '1906-12-09', '222334444', 'GH7654321'),
    ('Grace',     'Murray',   '1906-12-09', '222334444', 'GM0000002'),
    ('Alan',      'Turing',   '1912-06-23', '333445555', 'AT1111111'),
    ('Katherine', 'Johnson',  '1918-08-26', '555667777', 'KJ2222222'),
    ('Edsger',    'Dijkstra', '1930-05-11', '666778888', 'ED3333333');
