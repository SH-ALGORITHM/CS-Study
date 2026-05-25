-- reserved_by: 누가 예매했는지 기록 (비어있으면 NULL)
CREATE TABLE IF NOT EXISTS seat (
                                  id SERIAL PRIMARY KEY,
                                  concert_name VARCHAR(100) NOT NULL,
  seat_no INT NOT NULL,
  reserved_by VARCHAR(50) DEFAULT NULL,
  version BIGINT NOT NULL DEFAULT 0
  );

-- 2. 사용자 지갑 테이블 만들기
CREATE TABLE IF NOT EXISTS user_wallet (
                                         user_id VARCHAR(50) PRIMARY KEY,
  balance NUMERIC NOT NULL DEFAULT 100000,
  version BIGINT NOT NULL DEFAULT 0
  );

TRUNCATE seat, user_wallet;

INSERT INTO seat (id, concert_name, seat_no) VALUES (1, 'TWICE Concert', 1), (2, 'TWICE Concert', 2);
INSERT INTO user_wallet (user_id, balance) VALUES ('UserA', 100000), ('UserB', 100000);
