-- 3주차 stage 1 환전

--셋팅
docker exec -it csstudy-postgres psql -U csstudy -d csstudy
SELECT pg_backend_pid();

-- 테이블
CREATE TABLE IF NOT EXISTS wallet03 (
    user_id  BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    balance  BIGINT NOT NULL,
    version  BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, currency)
);
INSERT INTO wallet03 (user_id, currency, balance) VALUES (1, 'KRW', 1000000), (1, 'USD', 1000)
ON CONFLICT (user_id, currency) DO UPDATE SET balance = EXCLUDED.balance, version = 0;




-- 1-1 SELECT FOR UPDATE
-- FOR UPDATE가 다른 세션을 어떻게 막는 지

-- 세션 A
BEGIN;
SELECT * FROM wallet03 WHERE user_id = 1 AND currency = 'KRW' FOR UPDATE;
-- A가 KRW row에 X-lock 획득. 여기서 멈추고 세션 B로 이동.

-- 세션 B
-- 1. SELECT (MVCC lock-free라 안 막힘)
SELECT * FROM wallet03 WHERE user_id = 1 AND currency = 'KRW';

-- 2. UPDATE (X-lock 대기)
UPDATE wallet03 SET balance = 50000 WHERE user_id = 1 AND currency = 'KRW';

-- 3. FOR UPDATE (X-lock 대기)
SELECT * FROM wallet03 WHERE user_id = 1 AND currency = 'KRW' FOR UPDATE;

-- 4. FOR SHARE (S-lock 차단)
SELECT * FROM wallet03 WHERE user_id = 1 AND currency = 'KRW' FOR SHARE;

-- 세션 A
COMMIT;




-- 1-2. 데드락

-- 두 row를 다른 순서로 잡음
UPDATE wallet03 SET balance = 1000000, version = 0 WHERE user_id = 1 AND currency = 'KRW';
UPDATE wallet03 SET balance = 1000, version = 0 WHERE user_id = 1 AND currency = 'USD';

-- 세션 A (KRW 먼저 잡기)
BEGIN;
UPDATE wallet03 SET balance = balance - 1000 WHERE user_id = 1 AND currency = 'KRW';


-- 세션 B (USD 먼저 잡기)
BEGIN;
UPDATE wallet03 SET balance = balance - 1 WHERE user_id = 1 AND currency = 'USD';
-- USD row에 X-lock 획득. 여기서 멈추고 세션 A로 이동.

-- 세션 A (USD lock 시도 - B가 잡고 있어서 대기)
UPDATE wallet03 SET balance = balance + 1 WHERE user_id = 1 AND currency = 'USD';

-- 세션 B (KRW lock 시도 - A가 잡고 있어서 대기 - 순환 대기 - 데드락)
UPDATE wallet03 SET balance = balance + 1000 WHERE user_id = 1 AND currency = 'KRW';

-- Coffman 4조건
-- 1. 상호 배제: row X-lock은 한 tx만 보유 가능
-- 2. 점유 대기: A가 KRW lock 잡은 채로 USD lock 기다림
-- 3. 비선점: B의 USD lock을 A가 강제로 뺏을 수 없음
-- 4. 순환 대기: A->B(USD 기다림)->A(KRW 기다림) 순환


COMMIT;
ROLLBACK;


-- 1-3. Redis 분산락
docker exec -it csstudy-redis redis-cli

-- 창 A: 잠금 시도
SET lock:wallet03:1:exchange "session-A" NX EX 10

-- 창 B: 같은 키 잠금 시도
SET lock:wallet03:1:exchange "session-B" NX EX 10

-- 창 A: 해제
DEL lock:wallet03:1:exchange

-- 창 B: 재시도
SET lock:wallet03:1:exchange "session-B" NX EX 10


-- 1-4. 현재 락 상태 확인

SELECT
    pid,
    locktype,
    relation::regclass AS table_name,
    mode,
    granted
FROM pg_locks
WHERE relation IS NOT NULL
  AND relation::regclass::text = 'wallet03'
ORDER BY pid;
