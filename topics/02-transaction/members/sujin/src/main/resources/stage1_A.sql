-- ============================================================
-- 1. READ UNCOMMITTED - Dirty Read 확인
-- PostgreSQL에서는 READ UNCOMMITTED도 READ COMMITTED처럼 동작하므로
-- 세션 B가 A의 미커밋 UPDATE를 읽지 못해야 한다.
-- ============================================================

-- [A-1] 미커밋 변경 만들기
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
SHOW TRANSACTION ISOLATION LEVEL;

UPDATE seat
SET reserved_by = 1001,
    reserved_at = NOW()
WHERE concert_id = 1
  AND seat_no = 'A1';

-- 여기서 COMMIT하지 말고 세션 B의 [B-1] SELECT 실행

-- [A-2] 세션 B 확인 후 롤백
ROLLBACK;


-- ============================================================
-- 2. READ COMMITTED - Non-repeatable Read 재현
-- 같은 트랜잭션 안에서 세션 B가 같은 row를 두 번 읽었는데,
-- 세션 A의 COMMIT 이후 값이 달라지는지 확인한다.
-- ============================================================

-- [A-3] 세션 B가 첫 SELECT를 끝낸 뒤 실행
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
SHOW TRANSACTION ISOLATION LEVEL;

UPDATE seat
SET reserved_by = 1001,
    reserved_at = NOW()
WHERE concert_id = 1
  AND seat_no = 'A1';

COMMIT;

-- 이후 세션 B의 [B-3] 두 번째 SELECT 실행


-- ============================================================
-- 3. READ COMMITTED - Phantom Read 재현
-- 세션 B가 조건 COUNT를 먼저 조회한 뒤,
-- 세션 A가 조건에 맞는 새로운 row를 INSERT + COMMIT한다.
-- ============================================================

-- [A-4] 세션 B가 첫 COUNT를 끝낸 뒤 실행
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
SHOW TRANSACTION ISOLATION LEVEL;

INSERT INTO seat (concert_id, seat_no, reserved_by, reserved_at)
VALUES (2, 'A1', NULL, NULL);

COMMIT;

-- 이후 세션 B의 [B-5] 두 번째 COUNT 실행


-- ============================================================
-- 4. READ COMMITTED - Lost Update 재현
-- A와 B가 둘 다 A1을 NULL로 읽고 앱에서 예약 가능하다고 판단한 뒤,
-- 각자 절대값 UPDATE를 수행한다.
-- 최종 reserved_by가 마지막 COMMIT 사용자로 덮이면 Lost Update.
-- ============================================================

-- [A-5] 먼저 초기화 후 실행
TRUNCATE TABLE seat;
INSERT INTO seat (concert_id, seat_no, reserved_by, reserved_at)
SELECT 1, 'A' || g, NULL, NULL
FROM generate_series(1, 100) AS g;

BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
SHOW TRANSACTION ISOLATION LEVEL;

SELECT reserved_by
FROM seat
WHERE concert_id = 1
  AND seat_no = 'A1';
-- 기대 결과: NULL
-- 여기서 앱이 "예약 가능"하다고 판단했다고 가정

-- 세션 B의 [B-6] SELECT 실행 후 아래 UPDATE 실행

-- [A-6]
UPDATE seat
SET reserved_by = 1001,
    reserved_at = NOW()
WHERE concert_id = 1
  AND seat_no = 'A1';

COMMIT;

-- 이후 세션 B의 [B-7] UPDATE + COMMIT 실행


-- ============================================================
-- 5. REPEATABLE READ - Lost Update 차단 확인
-- PostgreSQL에서는 두 번째 UPDATE 시점에
-- ERROR: could not serialize access due to concurrent update 가 발생해야 한다.
-- ============================================================

-- [A-7] 먼저 초기화 후 실행
TRUNCATE TABLE seat;
INSERT INTO seat (concert_id, seat_no, reserved_by, reserved_at)
SELECT 1, 'A' || g, NULL, NULL
FROM generate_series(1, 100) AS g;

BEGIN;
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
SHOW TRANSACTION ISOLATION LEVEL;

SELECT reserved_by
FROM seat
WHERE concert_id = 1
  AND seat_no = 'A1';
-- 기대 결과: NULL

-- 세션 B의 [B-8] SELECT 실행 후 아래 UPDATE 실행

-- [A-8]
UPDATE seat
SET reserved_by = 1001,
    reserved_at = NOW()
WHERE concert_id = 1
  AND seat_no = 'A1';

COMMIT;

-- 이후 세션 B의 [B-9] UPDATE 실행 시 에러 확인


-- ============================================================
-- 6. SERIALIZABLE - Lost Update 차단 확인
-- REPEATABLE READ와 마찬가지로 concurrent update 에러가 발생해야 한다.
-- ============================================================

-- [A-9] 먼저 초기화 후 실행
TRUNCATE TABLE seat;
INSERT INTO seat (concert_id, seat_no, reserved_by, reserved_at)
SELECT 1, 'A' || g, NULL, NULL
FROM generate_series(1, 100) AS g;

BEGIN;
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
SHOW TRANSACTION ISOLATION LEVEL;

SELECT reserved_by
FROM seat
WHERE concert_id = 1
  AND seat_no = 'A1';
-- 기대 결과: NULL

-- 세션 B의 [B-10] SELECT 실행 후 아래 UPDATE 실행

-- [A-10]
UPDATE seat
SET reserved_by = 1001,
    reserved_at = NOW()
WHERE concert_id = 1
  AND seat_no = 'A1';

COMMIT;

-- 이후 세션 B의 [B-11] UPDATE 실행 시 에러 확인


-- ============================================================
-- 7. Deadlock 재현
-- A는 A1 -> A2 순서로 업데이트하고,
-- B는 A2 -> A1 순서로 업데이트한다.
-- 서로 상대가 잡은 row를 기다리면서 deadlock detected가 발생한다.
-- ============================================================

-- [A-11] 먼저 초기화 후 실행
TRUNCATE TABLE seat;
INSERT INTO seat (concert_id, seat_no, reserved_by, reserved_at)
SELECT 1, 'A' || g, NULL, NULL
FROM generate_series(1, 100) AS g;

BEGIN;

UPDATE seat
SET reserved_by = 1001,
    reserved_at = NOW()
WHERE concert_id = 1
  AND seat_no = 'A1';

-- 세션 B의 [B-12] 실행 후 아래 실행

-- [A-12] B가 A2를 잡고 있으므로 대기 상태가 된다.
UPDATE seat
SET reserved_by = 1001,
    reserved_at = NOW()
WHERE concert_id = 1
  AND seat_no = 'A2';

-- 세션 B의 [B-13] 실행 시 둘 중 하나에서 deadlock detected 발생
COMMIT;
