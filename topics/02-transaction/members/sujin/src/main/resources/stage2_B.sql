-- ============================================================
-- 1. READ UNCOMMITTED - Dirty Read 확인
-- ============================================================

-- [B-1] 세션 A의 [A-1] UPDATE 이후, A가 COMMIT하기 전에 실행
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
SHOW TRANSACTION ISOLATION LEVEL;

SELECT reserved_by
FROM seat
WHERE concert_id = 1
  AND seat_no = 'A1';
-- PostgreSQL 기대 결과: NULL
-- 1001이 보이면 Dirty Read지만, PostgreSQL에서는 보이지 않는 것이 정상

COMMIT;

-- 이후 세션 A의 [A-2] ROLLBACK 실행


-- ============================================================
-- 2. READ COMMITTED - Non-repeatable Read 재현
-- ============================================================

-- [B-2] 첫 번째 조회
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
SHOW TRANSACTION ISOLATION LEVEL;

SELECT reserved_by
FROM seat
WHERE concert_id = 1
  AND seat_no = 'A1';
-- 기대 결과: NULL

-- 여기서 COMMIT하지 말고 세션 A의 [A-3] UPDATE + COMMIT 실행

-- [B-3] 같은 트랜잭션 안에서 다시 조회
SELECT reserved_by
FROM seat
WHERE concert_id = 1
  AND seat_no = 'A1';
-- 기대 결과: 1001
-- 같은 트랜잭션 안에서 값이 바뀌었으므로 Non-repeatable Read

COMMIT;


-- ============================================================
-- 3. READ COMMITTED - Phantom Read 재현
-- ============================================================

-- [B-4] 첫 번째 COUNT
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
SHOW TRANSACTION ISOLATION LEVEL;

SELECT COUNT(*)
FROM seat
WHERE concert_id = 2;
-- 기대 결과: 0

-- 여기서 COMMIT하지 말고 세션 A의 [A-4] INSERT + COMMIT 실행

-- [B-5] 같은 조건으로 다시 COUNT
SELECT COUNT(*)
FROM seat
WHERE concert_id = 2;
-- 기대 결과: 1
-- 같은 조건인데 row 개수가 바뀌었으므로 Phantom Read

COMMIT;


-- ============================================================
-- 4. READ COMMITTED - Lost Update 재현
-- ============================================================

-- [B-6] 세션 A의 [A-5] SELECT 이후 실행
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
SHOW TRANSACTION ISOLATION LEVEL;

SELECT reserved_by
FROM seat
WHERE concert_id = 1
  AND seat_no = 'A1';
-- 기대 결과: NULL
-- 여기서 앱이 "예약 가능"하다고 판단했다고 가정

-- 세션 A의 [A-6] UPDATE + COMMIT 실행 후 아래 실행

-- [B-7]
UPDATE seat
SET reserved_by = 2002,
    reserved_at = NOW()
WHERE concert_id = 1
  AND seat_no = 'A1';

COMMIT;

SELECT reserved_by
FROM seat
WHERE concert_id = 1
  AND seat_no = 'A1';
-- 기대 결과: 2002
-- A의 1001 예약이 B의 2002로 덮였으므로 Lost Update


-- ============================================================
-- 5. REPEATABLE READ - Lost Update 차단 확인
-- ============================================================

-- [B-8] 세션 A의 [A-7] SELECT 이후 실행
BEGIN;
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
SHOW TRANSACTION ISOLATION LEVEL;

SELECT reserved_by
FROM seat
WHERE concert_id = 1
  AND seat_no = 'A1';
-- 기대 결과: NULL

-- 세션 A의 [A-8] UPDATE + COMMIT 실행 후 아래 실행

-- [B-9] PostgreSQL 기대 결과: ERROR: could not serialize access due to concurrent update
UPDATE seat
SET reserved_by = 2002,
    reserved_at = NOW()
WHERE concert_id = 1
  AND seat_no = 'A1';

COMMIT;
-- 에러가 발생했다면 COMMIT 대신 ROLLBACK 필요
-- ROLLBACK;


-- ============================================================
-- 6. SERIALIZABLE - Lost Update 차단 확인
-- ============================================================

-- [B-10] 세션 A의 [A-9] SELECT 이후 실행
BEGIN;
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
SHOW TRANSACTION ISOLATION LEVEL;

SELECT reserved_by
FROM seat
WHERE concert_id = 1
  AND seat_no = 'A1';
-- 기대 결과: NULL

-- 세션 A의 [A-10] UPDATE + COMMIT 실행 후 아래 실행

-- [B-11] PostgreSQL 기대 결과: ERROR: could not serialize access due to concurrent update
UPDATE seat
SET reserved_by = 2002,
    reserved_at = NOW()
WHERE concert_id = 1
  AND seat_no = 'A1';

COMMIT;
-- 에러가 발생했다면 COMMIT 대신 ROLLBACK 필요
-- ROLLBACK;


-- ============================================================
-- 7. Deadlock 재현
-- ============================================================

-- [B-12] 세션 A의 [A-11] 실행 이후 실행
BEGIN;

UPDATE seat
SET reserved_by = 2002,
    reserved_at = NOW()
WHERE concert_id = 1
  AND seat_no = 'A2';

-- 세션 A의 [A-12] 실행 후 아래 실행

-- [B-13] A가 A1을 잡고 있으므로 대기하다가 deadlock detected 발생 가능
UPDATE seat
SET reserved_by = 2002,
    reserved_at = NOW()
WHERE concert_id = 1
  AND seat_no = 'A1';

COMMIT;
