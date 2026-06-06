-- ============================================================
-- STAGE 3 — 복합 인덱스 + 커버링 인덱스
-- ============================================================

\timing on

-- 정리 — STAGE 1, 2 의 단일 인덱스 제거 (복합과의 비교 명확하게)
DROP INDEX IF EXISTS idx_post_author;
DROP INDEX IF EXISTS idx_post_title;


-- ────────────────────────────────────────────────────────────
-- 3-1. 복합 인덱스 + leftmost prefix
-- ────────────────────────────────────────────────────────────
CREATE INDEX idx_post_author_created ON post(author_id, created_at DESC);
ANALYZE post;

-- (a) 첫 컬럼만 — Index Scan OK
EXPLAIN ANALYZE
SELECT * FROM post WHERE author_id = 42;

-- (b) 첫 + 두 번째 — Index Scan OK + Sort 제거
EXPLAIN ANALYZE
SELECT * FROM post WHERE author_id = 42 ORDER BY created_at DESC LIMIT 10;

-- (c) 두 번째만 — Seq Scan (leftmost prefix 위반)
EXPLAIN ANALYZE
SELECT * FROM post WHERE created_at > NOW() - INTERVAL '1 day';

-- (d) 정렬만 (WHERE 없음) — leftmost 위반, Seq Scan
EXPLAIN ANALYZE
SELECT * FROM post ORDER BY created_at DESC LIMIT 10;


-- ────────────────────────────────────────────────────────────
-- 3-2. 커버링 인덱스 — Index Only Scan
-- ────────────────────────────────────────────────────────────
-- ★ VACUUM 필수 — visibility map 이 최신이어야 Heap Fetches = 0 (진짜 Index Only Scan)
--   seed 직후 autovacuum 안 돌았으면 plan 은 Index Only Scan 으로 나와도
--   실제는 heap 다 뒤지는 "Heap Fetches: N" 이 크게 나옴.
VACUUM post;

-- (a) SELECT 컬럼이 인덱스 안에 있으면 → Index Only Scan
EXPLAIN ANALYZE
SELECT author_id, created_at FROM post WHERE author_id = 42
ORDER BY created_at DESC LIMIT 10;
-- → 출력에서 "Heap Fetches: 0" 확인. 0 이면 진짜 테이블 접근 X

-- (b) title 도 가져오려면 → 테이블 접근 (Index Scan + Heap)
EXPLAIN ANALYZE
SELECT author_id, created_at, title FROM post WHERE author_id = 42
ORDER BY created_at DESC LIMIT 10;

-- 해결 — PostgreSQL INCLUDE 절로 커버링 인덱스
CREATE INDEX idx_post_covering ON post(author_id, created_at DESC) INCLUDE (title);
ANALYZE post;

EXPLAIN ANALYZE
SELECT author_id, created_at, title FROM post WHERE author_id = 42
ORDER BY created_at DESC LIMIT 10;
-- → "Index Only Scan" 확인


-- ────────────────────────────────────────────────────────────
-- 3-3. 복합 인덱스 순서 결정 — Cardinality + 등호 → 범위 순
-- ────────────────────────────────────────────────────────────
-- author_id (Cardinality 1000) + status (있으면 Cardinality 5) 가정 시
--   ★★ status, author_id   — Cardinality 낮은 status 가 먼저면 비효율
--   ★★★ author_id, status  — Cardinality 높은 author_id 먼저
--
-- 등호 → 범위 순:
--   WHERE a = ? AND b BETWEEN ? AND ?   → (a, b) — a 등호 먼저
--   WHERE a BETWEEN ? AND ? AND b = ?   → (b, a) — b 등호 먼저
