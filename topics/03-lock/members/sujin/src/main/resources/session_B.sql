SELECT pg_backend_pid();

-- 1) FOR UPDATE ==========================

-- 정상 실행
SELECT *
FROM wallet
WHERE user_id = 1;

-- 대기 -> A COMMIT 후 실행 재개
UPDATE wallet
SET cash = 900000
WHERE user_id = 1;

-- 대기 -> A COMMIT 후 실행 재개
SELECT *
FROM wallet
WHERE user_id = 1
  FOR UPDATE;


-- 2) 주식 도메인 데드락 재현 ==========================
BEGIN;

SELECT qty
FROM holding
WHERE user_id = 1 AND ticker = '005930'
  FOR UPDATE;

-- 세션 A의 자원 요청
SELECT *
FROM wallet
WHERE user_id = 1
  FOR UPDATE;
-- PostgreSQL이 데드락 감지 -> SQLState 40P01 발생

ROLLBACK;


-- 3) Redis 분산락 관찰 ==========================
SET lock:ticker:005930 "session-B" NX EX 10
-- (nil)

SET lock:ticker:005930 "session-B" NX EX 10 -- 다시

