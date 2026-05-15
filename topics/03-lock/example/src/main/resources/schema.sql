-- 3주차 — 계좌 이체 도메인 (락 학습용)
-- Java SchemaBootstrap 이 자동 실행 — 수동 셋업 시 참고용

CREATE TABLE IF NOT EXISTS account (
    id BIGINT PRIMARY KEY,
    balance NUMERIC NOT NULL,
    version BIGINT NOT NULL DEFAULT 0    -- 낙관적 락용
);

-- 두 계좌 시드 (데드락 학습을 위해 row 2 개 이상 필요)
INSERT INTO account (id, balance) VALUES (1, 10000), (2, 10000)
ON CONFLICT (id) DO UPDATE SET balance = EXCLUDED.balance, version = 0;
