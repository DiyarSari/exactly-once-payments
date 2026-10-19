-- Three demo accounts so the API can be tried right after startup.
INSERT INTO accounts (account_number, owner_name, currency, balance, opening_balance)
VALUES ('TR809999900000000000000001', 'Ayşe Yılmaz',  'TRY', 10000.00, 10000.00),
       ('TR539999900000000000000002', 'Mehmet Demir', 'TRY',  5000.00,  5000.00),
       ('TR269999900000000000000003', 'Zeynep Kaya',  'TRY',  2500.00,  2500.00);
