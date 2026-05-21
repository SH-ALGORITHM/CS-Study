CREATE TABLE IF NOT EXISTS wallet (
                                    user_id BIGINT PRIMARY KEY,
                                    cash NUMERIC NOT NULL,
                                    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS holding (
                                     user_id BIGINT NOT NULL,
                                     ticker VARCHAR(20) NOT NULL,
                                     qty BIGINT NOT NULL,
                                     version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, ticker)
);

INSERT INTO wallet (user_id, cash, version)
      VALUES (1, 1000000, 0)
        ON CONFLICT (user_id)
DO UPDATE SET cash = EXCLUDED.cash, version = 0;

INSERT INTO holding (user_id, ticker, qty, version)
      VALUES (1, '005930', 10, 0)
        ON CONFLICT (user_id, ticker)
DO UPDATE SET qty = EXCLUDED.qty, version = 0;
