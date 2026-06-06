-- ============================================================
-- STAGE 4 — 7 주차 JPA SQL EXPLAIN (살짝만)
-- ============================================================
-- 7 주차에서 본 N+1 / JOIN FETCH / 페이징 SQL 을 8 주차 도구로 점검.

\timing on

CREATE INDEX IF NOT EXISTS idx_comment_post ON comment(post_id);
ANALYZE comment;


-- ────────────────────────────────────────────────────────────
-- 4-1. 7 주차 N+1 의 회당 SQL
-- ────────────────────────────────────────────────────────────
-- 7 주차 Stage2_1 에서 본 회당 SQL — Post 마다 N 회 반복.

-- (a) post_id 인덱스 있을 때 — 회당 빠름
EXPLAIN ANALYZE
SELECT * FROM comment WHERE post_id = 42;

-- (b) 다른 post_id 도 — 회당 비슷한 시간
EXPLAIN ANALYZE
SELECT * FROM comment WHERE post_id = 100;

-- → 회당 SQL 자체는 Index Scan + 빠름.
--   하지만 N 회 자체의 네트워크 / 파싱 / 결과 매핑 오버헤드는 인덱스로 못 줄임.
--   7 주차 JOIN FETCH / @BatchSize 가 해결한 건 "회수 자체".


-- ────────────────────────────────────────────────────────────
-- 4-2. 7 주차 JOIN FETCH 의 SQL
-- ────────────────────────────────────────────────────────────
EXPLAIN ANALYZE
SELECT p.*, c.*
FROM post p
LEFT JOIN comment c ON c.post_id = p.id
WHERE p.author_id = 42;

-- 관찰 포인트:
--   · Join 알고리즘 — Hash Join / Nested Loop / Merge Join 중 옵티마이저 선택
--   · 결과 row 수 (N × M) 가 클수록 비싸짐
--   · author_id 인덱스 + post_id 인덱스 모두 있어야 빠름


-- ────────────────────────────────────────────────────────────
-- 4-3. OFFSET 큰 페이징 함정 + cursor pagination
-- ────────────────────────────────────────────────────────────
-- (a) OFFSET 작음 — 빠름
EXPLAIN ANALYZE
SELECT * FROM post ORDER BY id LIMIT 10 OFFSET 10;

-- (b) OFFSET 큼 — 100010 row 읽고 10 만 반환
EXPLAIN ANALYZE
SELECT * FROM post ORDER BY id LIMIT 10 OFFSET 100000;

-- (c) cursor pagination — 이전 페이지 마지막 id 를 cursor 로
--      Index Scan + LIMIT 10 만 읽음
EXPLAIN ANALYZE
SELECT * FROM post WHERE id < 100000 ORDER BY id DESC LIMIT 10;

-- 측정 비교:
--   (a) OFFSET 10:      0.5ms
--   (b) OFFSET 100000:  100ms ~ 5 초
--   (c) cursor:         0.5ms (= OFFSET 작음과 같음)
--
-- → 무한 스크롤 / 큰 페이지는 cursor pagination 자연.
