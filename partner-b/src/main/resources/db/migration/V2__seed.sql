insert into identity (first_name, last_name, birth_date, p_number, document_number) values
    ('Ada',       'Lovelace', '1815-12-10', 'PN1001', 'L10000001'),
    ('Ada',       'Lovelace', '1815-12-10', 'PN9001', 'L10000001'), -- same license, other PNumber
    ('Ada',       'King',     '1816-01-01', 'PN1001', 'K99999999'), -- same PNumber, other license
    ('Grace',     'Hopper',   '1906-12-09', 'PN1002', 'H20000002'),
    ('Katherine', 'Johnson',  '1918-08-26', 'PN1003', 'J30000003'),
    ('Alan',      'Turing',   '1912-06-23', 'PN1004', 'T40000004'),
    ('Dorothy',   'Vaughan',  '1910-09-20', 'PN1005', 'V50000005'),
    ('Mary',      'Jackson',  '1921-04-09', 'PN1006', 'J60000006');
