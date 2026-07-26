-- 6주차 STAGE 2 — 결제(payment) 도메인 트랜잭션 결합용 스키마
-- IF NOT EXISTS: 매 실행마다 schema.sql 이 돌아도 안전 (H2 mem 은 어차피 재생성)

CREATE TABLE IF NOT EXISTS payment (
    id     BIGINT        PRIMARY KEY,
    amount DECIMAL(15, 2) NOT NULL
);

-- 2-5 BEFORE_COMMIT 영수증 발급 실습용 (같은 트랜잭션 안에서 INSERT).
-- 컬럼/설계는 네 도메인에 맞게 바꿔도 OK.
CREATE TABLE IF NOT EXISTS receipt (
    payment_id BIGINT    PRIMARY KEY,
    issued_at  TIMESTAMP NOT NULL
);

-- 2-5 AFTER_COMMIT 포인트 적립 실습용. AFTER_COMMIT 리스너에서 DB 쓰기 →
-- REQUIRES_NEW 없으면 조용히 유실되는 함정 확인용.
CREATE TABLE IF NOT EXISTS point (
    payment_id BIGINT        PRIMARY KEY,
    amount     DECIMAL(15, 2) NOT NULL
);
