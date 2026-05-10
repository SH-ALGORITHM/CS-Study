-- ============================================================================
-- STAGE 1 — 호텔 객실 예약 도메인 손 측정 (DBeaver 두 세션)
--
-- 사용법:
--   1. DBeaver SQL Editor 두 개 (세션 A, B)
--   2. 각 창에서 SELECT pg_backend_pid(); 실행 → PID 다른지 확인
--   3. 아래 실험을 순서대로 진행 — 각 명령 드래그 + Cmd+Enter
--   4. 결과는 measurements.md 의 STAGE 1 표에 채워넣기
--
-- Part A: 호텔 도메인 자연 race (INSERT race + Phantom Read)
-- Part B: 학습 목적 추가 측정 (Dirty / NR / Lost Update / pg_locks)
-- ============================================================================


-- ─────────────────────────────────────────────────────────────────
-- 사전 셋업 — schema.sql 적용 후 한 번만
-- 학습 목적 임시 numeric 컬럼 추가 (Lost Update 실험용)
-- 호텔 도메인엔 줄어드는 balance 가 자연스럽게 없으므로
-- ─────────────────────────────────────────────────────────────────

ALTER TABLE room ADD COLUMN IF NOT EXISTS available_count INTEGER DEFAULT 100;
UPDATE room SET available_count = 100 WHERE room_no = 101;
SELECT room_no, room_type, available_count FROM room WHERE room_no = 101;
-- 기대: (101, 'standard', 100)


-- ─────────────────────────────────────────────────────────────────
-- 두 세션 분리 확인 (각 창에서)
-- ─────────────────────────────────────────────────────────────────

-- 세션 A:
SELECT pg_backend_pid();
-- 세션 B:
SELECT pg_backend_pid();
-- 두 PID 가 달라야 정상



-- ╔═══════════════════════════════════════════════════════════════════════════
-- ║ PART A — 호텔 도메인 자연 race (INSERT race + Phantom Read)
-- ║ 매 실험 전 room_booking 비우기:
-- ║   TRUNCATE room_booking RESTART IDENTITY;
-- ╚═══════════════════════════════════════════════════════════════════════════


-- ============================================================================
-- A-1: 이중 예약 재현 — READ COMMITTED
-- ============================================================================

TRUNCATE room_booking RESTART IDENTITY;

-- ─── 세션 A ───
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
SELECT 1 FROM room_booking
WHERE room_no = 101
  AND daterange(check_in, check_out) && daterange('2026-05-08', '2026-05-09');
-- 0 row

-- ─── 세션 B ───
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
SELECT 1 FROM room_booking
WHERE room_no = 101
  AND daterange(check_in, check_out) && daterange('2026-05-08', '2026-05-09');
-- 0 row

-- ─── 세션 A ───
INSERT INTO room_booking (room_no, check_in, check_out, guest_name)
VALUES (101, '2026-05-08', '2026-05-09', 'Alice');
COMMIT;

-- ─── 세션 B ───
INSERT INTO room_booking (room_no, check_in, check_out, guest_name)
VALUES (101, '2026-05-08', '2026-05-09', 'Bob');
COMMIT;

SELECT * FROM room_booking;
-- 기대: 두 row (Alice + Bob) → 이중 예약 발생


-- ============================================================================
-- A-2: 이중 예약 재현 — REPEATABLE READ
-- ============================================================================

TRUNCATE room_booking RESTART IDENTITY;

-- A: BEGIN; SET RR; SELECT (0 row);
-- B: BEGIN; SET RR; SELECT (0 row);
-- A: INSERT Alice + COMMIT;
-- B: INSERT Bob + COMMIT;
-- 결과: 두 row (RR 도 INSERT race 못 막음)


-- ============================================================================
-- A-3: SERIALIZABLE — SELECT 없이 (read set 비어있음)
-- ============================================================================

TRUNCATE room_booking RESTART IDENTITY;

-- ─── 세션 A ───
BEGIN;
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
INSERT INTO room_booking (room_no, check_in, check_out, guest_name)
VALUES (101, '2026-05-08', '2026-05-09', 'Alice');
COMMIT;

-- ─── 세션 B ───
BEGIN;
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
INSERT INTO room_booking (room_no, check_in, check_out, guest_name)
VALUES (101, '2026-05-08', '2026-05-09', 'Bob');
COMMIT;
-- 결과: 두 row 다 들어감 (read set 비어있어 SSI 가 의존성 추적 X)


-- ============================================================================
-- A-4: SERIALIZABLE — SELECT 포함 (★ SSI 작동)
-- ============================================================================

TRUNCATE room_booking RESTART IDENTITY;

-- ─── 세션 A ───
BEGIN;
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
SELECT 1 FROM room_booking
WHERE room_no = 101
  AND daterange(check_in, check_out) && daterange('2026-05-08', '2026-05-09');
-- read set 등록

-- ─── 세션 B ───
BEGIN;
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
SELECT 1 FROM room_booking
WHERE room_no = 101
  AND daterange(check_in, check_out) && daterange('2026-05-08', '2026-05-09');
-- read set 등록

-- ─── 세션 A ───
INSERT INTO room_booking (room_no, check_in, check_out, guest_name)
VALUES (101, '2026-05-08', '2026-05-09', 'Alice');
COMMIT;

-- ─── 세션 B ───
INSERT INTO room_booking (room_no, check_in, check_out, guest_name)
VALUES (101, '2026-05-08', '2026-05-09', 'Bob');
COMMIT;
-- 기대: SQLState 40001 (could not serialize access due to read/write dependencies)


-- ============================================================================
-- A-5: Phantom Read — RC
-- ============================================================================

TRUNCATE room_booking RESTART IDENTITY;

-- ─── 세션 B ───
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
SELECT COUNT(*) FROM room_booking
WHERE room_no = 101
  AND daterange(check_in, check_out) && daterange('2026-05-08', '2026-05-09');
-- 첫 결과: 0

-- ─── 세션 A ───
BEGIN;
INSERT INTO room_booking (room_no, check_in, check_out, guest_name)
VALUES (101, '2026-05-08', '2026-05-09', 'Charlie');
COMMIT;

-- ─── 세션 B 다시 ───
SELECT COUNT(*) FROM room_booking
WHERE room_no = 101
  AND daterange(check_in, check_out) && daterange('2026-05-08', '2026-05-09');
-- 두 번째 결과: 1 (Phantom Read 발생)
COMMIT;


-- ============================================================================
-- A-6: Phantom Read — REPEATABLE READ
-- ============================================================================

TRUNCATE room_booking RESTART IDENTITY;

-- ─── 세션 B ───
BEGIN;
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
SELECT COUNT(*) FROM room_booking
WHERE room_no = 101
  AND daterange(check_in, check_out) && daterange('2026-05-08', '2026-05-09');
-- 첫 결과: 0

-- ─── 세션 A ───
BEGIN;
INSERT INTO room_booking (room_no, check_in, check_out, guest_name)
VALUES (101, '2026-05-08', '2026-05-09', 'Charlie');
COMMIT;

-- ─── 세션 B 다시 ───
SELECT COUNT(*) FROM room_booking
WHERE room_no = 101
  AND daterange(check_in, check_out) && daterange('2026-05-08', '2026-05-09');
-- 두 번째 결과: 0 (PG 의 RR 은 스냅샷 격리라 Phantom 도 차단)
COMMIT;



-- ╔═══════════════════════════════════════════════════════════════════════════
-- ║ PART B — 학습 목적 추가 측정
-- ║ 호텔 도메인엔 자연스럽지 않은 anomaly. account.balance 등가로 임시 컬럼 사용
-- ╚═══════════════════════════════════════════════════════════════════════════


-- ============================================================================
-- B-1: READ UNCOMMITTED 매핑 확인 (1 분)
-- ============================================================================

BEGIN;
SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
SHOW TRANSACTION ISOLATION LEVEL;
COMMIT;
-- 관찰: SHOW 결과가 'read uncommitted' 인지 'read committed' 인지
-- → PG 는 RU 를 RC 로 매핑한다는 게 정설. 본인 눈으로 확인


-- ============================================================================
-- B-2: Dirty Read — room.room_type UPDATE 시나리오
-- 4 격리 수준에서 반복 (RU / RC / RR / SR)
-- ============================================================================

UPDATE room SET room_type = 'standard' WHERE room_no = 101;

-- ─── 세션 A ───
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;  -- 4 단계 각각 변경하며 반복
UPDATE room SET room_type = 'deluxe' WHERE room_no = 101;
-- COMMIT 아직 X

-- ─── 세션 B ───
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
SELECT room_type FROM room WHERE room_no = 101;
-- 결과: 'standard' (Dirty Read 차단) 또는 'deluxe' (Dirty Read 발생) ?
COMMIT;

-- ─── 세션 A 정리 ───
ROLLBACK;


-- ============================================================================
-- B-3: Non-repeatable Read
-- 4 격리 수준에서 반복
-- ============================================================================

UPDATE room SET room_type = 'standard' WHERE room_no = 101;

-- ─── 세션 B ───
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;  -- 4 단계 각각
SELECT room_type FROM room WHERE room_no = 101;  -- 첫 SELECT
-- 결과 기록: ___

-- ─── 세션 A ───
BEGIN;
UPDATE room SET room_type = 'deluxe' WHERE room_no = 101;
COMMIT;

-- ─── 세션 B 다시 ───
SELECT room_type FROM room WHERE room_no = 101;  -- 두 번째 SELECT
-- 첫 결과와 같으면 NR 차단, 다르면 NR 발생
COMMIT;


-- ============================================================================
-- B-4: Lost Update — atomic UPDATE 패턴
-- (PG row-lock 이 자동으로 막아주는지 확인)
-- ============================================================================

UPDATE room SET available_count = 100 WHERE room_no = 101;

-- ─── 세션 A ───
BEGIN;
UPDATE room SET available_count = available_count - 10 WHERE room_no = 101;
-- COMMIT 아직 X

-- ─── 세션 B ───
BEGIN;
UPDATE room SET available_count = available_count - 10 WHERE room_no = 101;
-- ← 멈춤 (A 가 row 잡고 있어 대기)

-- ─── 세션 A ───
COMMIT;
-- → B 의 대기 풀림. B 가 다시 읽어서 90 - 10 = 80 으로 자동 계산

-- ─── 세션 B ───
COMMIT;

SELECT available_count FROM room WHERE room_no = 101;
-- 기대: 80 (PG row-lock 이 자동 직렬화 → Lost Update 안 보임)


-- ============================================================================
-- B-5: Lost Update — RMW 패턴 (★ 1주차 count++ 와 같은 구조)
-- READ COMMITTED 와 REPEATABLE READ 둘 다 시도
-- ============================================================================

UPDATE room SET available_count = 100 WHERE room_no = 101;

-- ─── 세션 A ───
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;  -- 그 다음 RR 로 재시도
SELECT available_count FROM room WHERE room_no = 101;  -- 결과: 100

-- ─── 세션 B ───
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
SELECT available_count FROM room WHERE room_no = 101;  -- 결과: 100

-- 두 세션 다 100 봤음. 각자 머리에서 100 - 10 = 90 계산.

-- ─── 세션 A ───
UPDATE room SET available_count = 90 WHERE room_no = 101;
COMMIT;

-- ─── 세션 B ───
UPDATE room SET available_count = 90 WHERE room_no = 101;  -- A 의 변경 덮어씀
COMMIT;

SELECT available_count FROM room WHERE room_no = 101;
-- READ COMMITTED 결과: 90 (둘 다 -10 했는데 90, 정답 80 → Lost Update 발생)
-- REPEATABLE READ 재시도: B 의 두 번째 UPDATE 에서 SQLState 40001
--   (ERROR: could not serialize access due to concurrent update)


-- ============================================================================
-- B-6: pg_locks / pg_stat_activity 락 대기 직접 보기
-- ============================================================================

UPDATE room SET available_count = 100 WHERE room_no = 101;

-- ─── 세션 A ───
BEGIN;
UPDATE room SET available_count = 50 WHERE room_no = 101;
-- COMMIT 아직 X (락 잡고 대기)

-- ─── 세션 B ───
BEGIN;
UPDATE room SET available_count = 30 WHERE room_no = 101;
-- → 멈춤 (A 가 잡은 row lock 풀리길 대기)

-- ─── 세션 C (관찰용 — 새 SQL Editor 또 하나) ───
SELECT pid, query, state, wait_event_type, wait_event
FROM pg_stat_activity
WHERE state != 'idle';
-- → 어느 PID 가 어떤 wait_event 로 대기 중인지

SELECT locktype, relation::regclass, mode, granted
FROM pg_locks
WHERE NOT granted OR mode LIKE '%Exclusive%';
-- → 어느 PID 가 어떤 락을 잡았고 어느 PID 가 대기 중인지

-- ─── 정리 ───
-- 세션 A:
ROLLBACK;
-- 세션 B 의 대기 풀림. 마무리:
-- 세션 B:
ROLLBACK;



-- ╔═══════════════════════════════════════════════════════════════════════════
-- ║ 정리 — 측정 끝나면 임시 컬럼 드롭 + 도메인 원래 상태로
-- ╚═══════════════════════════════════════════════════════════════════════════

ALTER TABLE room DROP COLUMN IF EXISTS available_count;
UPDATE room SET room_type = 'standard' WHERE room_no = 101;
TRUNCATE room_booking RESTART IDENTITY;

SELECT * FROM room;
SELECT * FROM room_booking;
