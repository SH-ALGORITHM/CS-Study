CREATE TABLE IF NOT EXISTS account (
    id BIGINT PRIMARY KEY,
    balance DECIMAL(15, 2) NOT NULL
);

DELETE FROM account;
INSERT INTO account (id, balance) VALUES (1, 10000.00);
INSERT INTO account (id, balance) VALUES (2, 10000.00);
