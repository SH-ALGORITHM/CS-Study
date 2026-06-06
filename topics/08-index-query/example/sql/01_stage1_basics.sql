-- ============================================================
-- STAGE 1 — B+Tree + EXPLAIN — Seq Scan vs Index Scan
-- ============================================================
-- 같은 쿼리에 인덱스 추가 전 / 후 시간 비교.

\timing on

-- ────────────────────────────────────────────────────────────
-- 1-2. 인덱스 없이 — Seq Scan
-- ────────────────────────────────────────────────────────────
-- ★ LIMIT 10 으로 TOP-N 쿼리. 인덱스 효과가 가장 극명하게 드러나는 형태.
--   LIMIT 없이 1000 row 다 가져오면 옵티마이저가 Seq Scan 선택할 수 있음
--   (author 가 1000 명 균등 분포 → 0.1% 선택도이지만 row 가 흩뿌려져 있어
--    Bitmap Heap Scan 의 페이지 IO 가 Seq Scan 과 비슷해질 가능성).
EXPLAIN ANALYZE
SELECT * FROM post WHERE author_id = 42 ORDER BY created_at DESC LIMIT 10;

-- 출력 패턴:
--   Limit  (cost=...) (actual time=... rows=10 loops=1)
--     ->  Sort
--           ->  Seq Scan on post
--                 Filter: (author_id = 42)
--                 Rows Removed by Filter: 999990
--   Execution Time: ~ 150ms


-- ────────────────────────────────────────────────────────────
-- 1-3. 인덱스 추가 — Index Scan
-- ────────────────────────────────────────────────────────────
CREATE INDEX idx_post_author ON post(author_id);
ANALYZE post;

EXPLAIN ANALYZE
SELECT * FROM post WHERE author_id = 42 ORDER BY created_at DESC LIMIT 10;

-- 출력 패턴:
--   Limit  (actual time=... rows=10 loops=1)
--     ->  Sort
--           ->  Bitmap Heap Scan on post
--                 ->  Bitmap Index Scan on idx_post_author
--   Execution Time: ~ 2 ~ 5ms

-- → 30 ~ 60 배 차이. 본인 환경에서 직접 측정.
-- → 만약 인덱스 추가해도 여전히 Seq Scan 이면 — 선택도 0.1% 가 옵티마이저 기준
--    Seq 가 더 싸다고 판단. 더 낮은 선택도 (예: id = 42 PK 검색) 또는
--    복합 인덱스 (1-3 끝) 로 시연 강화.


-- ────────────────────────────────────────────────────────────
-- 1-4. INSERT 비용 — 인덱스 N 개 트레이드오프
-- ────────────────────────────────────────────────────────────
-- 독립 테이블 정의 (LIKE INCLUDING ALL 안 씀 — 그러면 본 post 의 인덱스도 복사됨)

-- (A) 인덱스 0 개 (PK 만)
CREATE TABLE post_a (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200),
    author_id BIGINT,
    code VARCHAR(20),
    created_at TIMESTAMP DEFAULT NOW()
);

EXPLAIN ANALYZE
INSERT INTO post_a (title, author_id, code)
SELECT 'X', 1, 'CODE' FROM generate_series(1, 100000);

-- (B) 인덱스 5 개
CREATE TABLE post_b (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200),
    author_id BIGINT,
    code VARCHAR(20),
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX ON post_b (author_id);
CREATE INDEX ON post_b (created_at);
CREATE INDEX ON post_b (title);
CREATE INDEX ON post_b (code);
CREATE INDEX ON post_b (LOWER(title));

EXPLAIN ANALYZE
INSERT INTO post_b (title, author_id, code)
SELECT 'X', 1, 'CODE' FROM generate_series(1, 100000);

-- 측정 비교:
--   (A) 인덱스 0 개: 100 ~ 300ms
--   (B) 인덱스 5 개: 500 ~ 1500ms (3 ~ 5 배)
--
-- → 무작정 인덱스 추가는 INSERT/UPDATE/DELETE 비용 발생. 본인 도메인에서 균형 결정.

DROP TABLE post_a;
DROP TABLE post_b;
