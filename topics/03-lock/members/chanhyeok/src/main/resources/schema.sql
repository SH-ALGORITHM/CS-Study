-- 3주차 · 본인 도메인 — P2P 송금 (락 학습용)
-- SchemaBootstrap 이 자동 실행. 수동 검증 시 참고용.

-- ====================================================================
-- user_wallet — 사용자 잔액 + 일일 누적 송금액 + version
-- ====================================================================
-- balance              잔액
-- daily_sent_amount    오늘 누적 송금액 (한도 검증용 RMW)
-- version              낙관적 락
CREATE TABLE IF NOT EXISTS user_wallet (
    id BIGINT PRIMARY KEY,
    balance NUMERIC NOT NULL,
    daily_sent_amount NUMERIC NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0
);

-- ====================================================================
-- fee_revenue — 플랫폼 수수료 수익 단일 row 핫스팟
-- ====================================================================
-- 모든 송금이 한 row 에 몰림 → 비관 vs 낙관 응답시간 역전 관찰 가능
CREATE TABLE IF NOT EXISTS fee_revenue (
    id BIGINT PRIMARY KEY,
    total_collected NUMERIC NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

-- ====================================================================
-- transfer_log — 송금 이력 (INSERT only)
-- ====================================================================
CREATE TABLE IF NOT EXISTS transfer_log (
    id BIGSERIAL PRIMARY KEY,
    from_id BIGINT NOT NULL,
    to_id BIGINT NOT NULL,
    amount NUMERIC NOT NULL,
    fee NUMERIC NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ====================================================================
-- 시드 — 두 사용자 + 수수료 수익 row 1 개
-- ====================================================================
-- 데드락 학습을 위해 user_wallet row 2 개 이상 필요
INSERT INTO user_wallet (id, balance, daily_sent_amount, version)
VALUES (1, 1000000, 0, 0), (2, 1000000, 0, 0)
ON CONFLICT (id) DO UPDATE
    SET balance = EXCLUDED.balance,
        daily_sent_amount = 0,
        version = 0;

INSERT INTO fee_revenue (id, total_collected, version)
VALUES (1, 0, 0)
ON CONFLICT (id) DO UPDATE
    SET total_collected = 0,
        version = 0;

-- transfer_log 는 매 측정마다 TRUNCATE (SchemaBootstrap 에서)
