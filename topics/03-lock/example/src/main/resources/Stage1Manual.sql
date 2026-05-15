-- ============================================================
-- 3주차 STAGE 1 — DBeaver / psql 두 세션 손 측정
-- 본인 도메인에 맞게 컬럼/테이블 이름 치환해서 사용
-- ============================================================

-- 사전 셋업 (한 번만)
CREATE TABLE IF NOT EXISTS account (
    id BIGINT PRIMARY KEY,
    balance NUMERIC NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
INSERT INTO account (id, balance) VALUES (1, 10000), (2, 10000)
ON CONFLICT (id) DO UPDATE SET balance = EXCLUDED.balance, version = 0;

-- 두 세션이 다른 커넥션인지 확인 (PID 달라야 정상)
SELECT pg_backend_pid();


-- ============================================================
-- 1-1. SELECT FOR UPDATE 동작 (X-lock)
-- ============================================================

-- 세션 A
BEGIN;
SELECT * FROM account WHERE id = 1 FOR UPDATE;
-- (대기)

-- 세션 B 에서 시도 — 각각 결과 관찰
SELECT * FROM account WHERE id = 1;                -- ① 일반 SELECT (안 막힘 — MVCC)
UPDATE account SET balance = 5000 WHERE id = 1;    -- ② UPDATE (막힘 — X-lock 대기)
SELECT * FROM account WHERE id = 1 FOR UPDATE;     -- ③ FOR UPDATE (막힘)
SELECT * FROM account WHERE id = 1 FOR SHARE;      -- ④ FOR SHARE (막힘 — X-lock 은 S-lock 차단)

-- 세션 A
COMMIT;
-- 세션 B 의 대기 풀림 — 어떻게 처리되는지 관찰


-- ============================================================
-- 1-2. 데드락 직접 재현 (Coffman 4 조건 매핑)
-- ============================================================

-- 세션 A
BEGIN;
UPDATE account SET balance = balance - 1000 WHERE id = 1;
-- (잠시 멈춤)

-- 세션 B
BEGIN;
UPDATE account SET balance = balance - 1000 WHERE id = 2;
-- (잠시 멈춤)

-- 세션 A — B 의 lock 대기
UPDATE account SET balance = balance + 1000 WHERE id = 2;

-- 세션 B — A 의 lock 대기 → 순환 대기 → PG 데드락 감지
UPDATE account SET balance = balance + 1000 WHERE id = 1;

-- PG 가 deadlock_timeout (1 초) 후 한쪽 abort:
--   ERROR:  deadlock detected
--   DETAIL: Process X waits for ShareLock ...
-- → 어느 쪽이 죽었는지 본인 관찰
-- → PG victim 선택 기준: 가장 적은 작업 한 쪽 (undo 비용 최소)

-- 살아남은 세션
COMMIT;
-- 죽은 세션
ROLLBACK;   -- 또는 자동 abort 됨


-- ============================================================
-- 1-3. Redis 분산락 손 측정 (redis-cli 두 창)
-- ============================================================

-- ※ 이 부분은 SQL 이 아닌 redis-cli 명령어. 별도 터미널 2 개 띄우기.
-- docker exec -it csstudy-redis redis-cli

-- 창 A
-- SET lock:account:1 "session-A" NX EX 10
-- → OK (잠금 성공)

-- 창 B
-- SET lock:account:1 "session-B" NX EX 10
-- → (nil) — 이미 잠겨있어 실패

-- 창 A
-- DEL lock:account:1
-- → 1 (해제)

-- 창 B
-- SET lock:account:1 "session-B" NX EX 10
-- → OK

-- TTL 자동 해제 확인 — 10 초 기다리면:
-- GET lock:account:1
-- → (nil)


-- ============================================================
-- 1-4. 정리 — pg_locks 로 현재 락 상태 확인
-- ============================================================

SELECT
    pid,
    locktype,
    relation::regclass AS table_name,
    mode,
    granted
FROM pg_locks
WHERE relation IS NOT NULL
  AND relation::regclass::text = 'account'
ORDER BY pid;
