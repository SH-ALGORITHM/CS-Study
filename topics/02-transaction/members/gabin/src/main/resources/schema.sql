CREATE TABLE IF NOT EXISTS stock (
                                   item_id BIGINT PRIMARY KEY,
                                   quantity INT NOT NULL
);

CREATE TABLE IF NOT EXISTS user_point (
                                        user_id BIGINT PRIMARY KEY,
                                        balance INT NOT NULL
);

TRUNCATE stock, user_point;

INSERT INTO stock (item_id, quantity) VALUES (1, 10);
INSERT INTO user_point (user_id, balance) VALUES (1, 10000);

SELECT * FROM stock;
SELECT * FROM user_point;
