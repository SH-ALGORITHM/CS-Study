-- 2주차 example 도메인 — 은행 계좌
--
-- 1주차 BankAccount 가 메모리 변수 차감이었다면, 이번엔 DB 컬럼 차감.
-- read-modify-write 패턴 (SELECT → 앱 계산 → UPDATE 절대값) 으로 의도적으로 짜서
-- Lost Update 가 격리 수준에 따라 어떻게 다르게 보이는지 확인.

CREATE TABLE IF NOT EXISTS account (
    id BIGINT PRIMARY KEY,
    balance BIGINT NOT NULL
);

-- STAGE 2/3 용 — 단일 계좌 race
INSERT INTO account (id, balance) VALUES (1, 100)
ON CONFLICT (id) DO UPDATE SET balance = EXCLUDED.balance;

-- STAGE 4 용 — 두 계좌 데드락
INSERT INTO account (id, balance) VALUES (2, 10000)
ON CONFLICT (id) DO UPDATE SET balance = EXCLUDED.balance;
