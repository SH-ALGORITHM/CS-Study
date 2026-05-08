-- ============================================================================
-- STAGE 1 — 손으로 격리 수준별 이상 현상 관찰 (DBeaver 두 세션 사용)
--
-- 사용법:
--   1. DBeaver 에서 SQL Editor 두 개 띄우기 (한 창은 A, 다른 창은 B)
--   2. 각 창에서 SELECT pg_backend_pid(); 실행 → PID 가 달라야 정상
--   3. 아래 실험을 순서대로 진행. 각 명령을 선택 (드래그) 후 Cmd+Enter
--   4. 각 격리 수준별로 실험 반복 — 4 격리 수준 × 5 실험 = 20 회
--   5. 본인 measurements.md 에 결과 표 + 관찰 노트 기록
--
-- 도메인: 계좌 이체 (account.balance) — 본인 도메인이면 컬럼/테이블 치환
-- ============================================================================


-- ─────────────────────────────────────────────────────────────────
-- 사전 셋업 — 한 번만 실행 (어느 세션이든 OK)
-- ─────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS account (
    id BIGINT PRIMARY KEY,
    balance BIGINT NOT NULL
);

INSERT INTO account (id, balance) VALUES (1, 100), (2, 10000)
ON CONFLICT (id) DO UPDATE SET balance = EXCLUDED.balance;

-- 확인
SELECT * FROM account;
-- 기대: (1, 100), (2, 10000)


-- ─────────────────────────────────────────────────────────────────
-- 두 세션 분리 확인 (각 창에서 한 번씩)
-- ─────────────────────────────────────────────────────────────────

-- 세션 A 창에서:
SELECT pg_backend_pid();

-- 세션 B 창에서 (다른 창):
SELECT pg_backend_pid();

-- 두 PID 가 달라야 정상. 같으면 같은 세션이라 격리 수준 실험 안 됨.


-- ============================================================================
-- 실험 1: Dirty Read
-- 한 트랜잭션이 commit 안 한 변경을 다른 트랜잭션이 볼 수 있는가?
-- ============================================================================

-- 실험 전 매번 초기화
UPDATE account SET balance = 100 WHERE id = 1;

-- 격리 수준 4 단계 각각에서 반복:
--   READ UNCOMMITTED / READ COMMITTED / REPEATABLE READ / SERIALIZABLE

-- ─── 세션 A ───
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;  -- ← 여기 바꿔가며 4 번
SHOW TRANSACTION ISOLATION LEVEL;                 -- 적용 확인
UPDATE account SET balance = 5000 WHERE id = 1;
-- COMMIT 아직 X — 세션 B 로 이동

-- ─── 세션 B ───
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
SELECT balance FROM account WHERE id = 1;
-- 결과: 100 ? 5000 ?
-- → 100 이면 Dirty Read 차단, 5000 이면 발생
COMMIT;

-- ─── 세션 A 마무리 ───
ROLLBACK;


-- ============================================================================
-- 실험 2: Non-repeatable Read
-- 같은 트랜잭션 안에서 같은 row 를 두 번 SELECT 했는데 결과가 다른가?
-- ============================================================================

UPDATE account SET balance = 100 WHERE id = 1;

-- ─── 세션 B ───
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
SELECT balance FROM account WHERE id = 1;   -- 첫 SELECT
-- 결과 기록: ___

-- ─── 세션 A ───
BEGIN;
UPDATE account SET balance = 200 WHERE id = 1;
COMMIT;

-- ─── 세션 B 다시 ───
SELECT balance FROM account WHERE id = 1;   -- 두 번째 SELECT
-- 결과 기록: ___
-- → 첫 결과와 다르면 Non-repeatable Read 발생
COMMIT;


-- ============================================================================
-- 실험 3: Phantom Read
-- 같은 조건 두 번 SELECT 했는데 row 개수가 다른가?
-- ============================================================================

DELETE FROM account WHERE id >= 100;  -- 깨끗한 상태로

-- ─── 세션 B ───
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
SELECT COUNT(*) FROM account WHERE balance >= 100;   -- 첫 COUNT
-- 결과 기록: ___

-- ─── 세션 A ───
BEGIN;
INSERT INTO account (id, balance) VALUES (101, 500);
COMMIT;

-- ─── 세션 B 다시 ───
SELECT COUNT(*) FROM account WHERE balance >= 100;   -- 두 번째 COUNT
-- 결과 기록: ___
-- → 첫 결과와 다르면 Phantom Read 발생
COMMIT;

-- 정리
DELETE FROM account WHERE id >= 100;


-- ============================================================================
-- 실험 4-A: Lost Update — atomic UPDATE 패턴 (한 문장)
-- PG row-lock 이 자동으로 막아주는지 확인
-- ============================================================================

UPDATE account SET balance = 100 WHERE id = 1;

-- ─── 세션 A ───
BEGIN;
UPDATE account SET balance = balance - 10 WHERE id = 1;
-- COMMIT 아직 X — 세션 B 로

-- ─── 세션 B ───
BEGIN;
UPDATE account SET balance = balance - 10 WHERE id = 1;
-- ← 이 문장 실행 후 멈춤? → A 가 row 잡고 있어 대기 중

-- ─── 세션 A ───
COMMIT;
-- → 세션 B 의 대기 풀림, 90 - 10 = 80 으로 자동 계산

-- ─── 세션 B ───
COMMIT;

-- 최종 잔고 확인
SELECT balance FROM account WHERE id = 1;
-- 기대: 80 (둘 다 정상 차감) → Lost Update 안 보임


-- ============================================================================
-- 실험 4-B: Lost Update — read-modify-write 패턴 (★ 핵심)
-- SELECT → 앱 계산 → UPDATE 절대값. 이 패턴은 격리 수준에 따라 다르게 동작
-- ============================================================================

UPDATE account SET balance = 100 WHERE id = 1;

-- ─── 세션 A ───
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
SELECT balance FROM account WHERE id = 1;     -- 결과: 100

-- ─── 세션 B ───
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
SELECT balance FROM account WHERE id = 1;     -- 결과: 100

-- 두 세션 다 100 봤음. 각자 머리에서 100 - 10 = 90 계산.

-- ─── 세션 A ───
UPDATE account SET balance = 90 WHERE id = 1;  -- 절대값 90 으로 세팅
COMMIT;

-- ─── 세션 B ───
UPDATE account SET balance = 90 WHERE id = 1;  -- A 의 90 을 그대로 90 으로 덮어씀
COMMIT;

-- 최종 잔고 확인
SELECT balance FROM account WHERE id = 1;
-- 결과: 90 (둘 다 출금 시도했는데 90, 정답은 80) → Lost Update 발생

-- 격리 수준 REPEATABLE READ 로 바꿔서 같은 시퀀스 → 두 번째 트랜잭션이 어떤 에러?
-- (힌트: SQLState 가 무엇인지 직접 확인)


-- ============================================================================
-- 실험 5: 락 / 대기 상태 관찰 (보너스)
-- 한 트랜잭션이 row 잡고 있을 때 시스템 뷰로 확인
-- ============================================================================

-- ─── 세션 A ───
BEGIN;
UPDATE account SET balance = 50 WHERE id = 1;
-- COMMIT 안 함

-- ─── 세션 B ───
BEGIN;
UPDATE account SET balance = 30 WHERE id = 1;
-- → 멈춤 (A 가 잡은 락 풀리길 대기 중)

-- ─── 세션 C (관찰용 — 새 SQL Editor 또 하나) ───
SELECT pid, query, state, wait_event_type, wait_event
FROM pg_stat_activity
WHERE state != 'idle';

SELECT locktype, relation::regclass, mode, granted
FROM pg_locks
WHERE NOT granted OR mode LIKE '%Exclusive%';

-- → 어느 PID 가 어느 락을 잡고 있고 어느 PID 가 대기 중인지 보임.

-- ─── 정리 ───
-- 세션 A:
ROLLBACK;
-- 세션 B 의 대기 풀림. 마무리:
-- 세션 B:
ROLLBACK;


-- ============================================================================
-- 정리 — 다음 실험 전 매번 깨끗한 상태로 되돌리기
-- ============================================================================

TRUNCATE account;
INSERT INTO account (id, balance) VALUES (1, 100), (2, 10000);


-- ============================================================================
-- 결과 정리 양식 — measurements.md 에 복사해 넣기
-- ============================================================================
/*
## STAGE 1 — 격리 수준별 이상 현상 관찰

| 격리 수준          | Dirty Read | Non-repeatable | Phantom | Lost Update (atomic) | Lost Update (RMW) |
|-------------------|------------|----------------|---------|----------------------|-------------------|
| READ UNCOMMITTED  | (관찰)     | (관찰)         | (관찰)  | (관찰)               | (관찰)            |
| READ COMMITTED    | ...        | ...            | ...    | ...                  | ...               |
| REPEATABLE READ   | ...        | ...            | ...    | ...                  | ...               |
| SERIALIZABLE      | ...        | ...            | ...    | ...                  | ...               |

관찰 노트:
- (예) READ UNCOMMITTED 에서 Dirty Read 가 안 보였다 — PG 가 RU 를 RC 로 매핑하기 때문 (PG 문서 확인)
- (예) REPEATABLE READ 에서 Lost Update (RMW) 시도 시 SQLState 40001 에러 발생
- (예) pg_locks 로 락 잡힌 row 확인됨
*/
