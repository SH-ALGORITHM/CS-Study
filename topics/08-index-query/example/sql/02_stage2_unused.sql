-- ============================================================
-- STAGE 2 — 인덱스 미사용 6 케이스 ★ 8 주차 가장 중요
-- ============================================================
-- 전제: STAGE 1 에서 idx_post_author 생성됨.

\timing on

-- 추가 인덱스
CREATE INDEX IF NOT EXISTS idx_post_title ON post(title);
CREATE INDEX IF NOT EXISTS idx_post_code ON post(code);
CREATE INDEX IF NOT EXISTS idx_post_deleted ON post(deleted_at);
ANALYZE post;


-- ────────────────────────────────────────────────────────────
-- 2-1. 함수 적용 — WHERE LOWER(col)
-- ────────────────────────────────────────────────────────────
-- (a) 함수 없이 — Index Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE title = 'Post #42';

-- (b) 함수 적용 — Seq Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE LOWER(title) = 'post #42';

-- 해결 — 함수 인덱스
CREATE INDEX idx_post_title_lower ON post(LOWER(title));
ANALYZE post;
EXPLAIN ANALYZE SELECT * FROM post WHERE LOWER(title) = 'post #42';


-- ────────────────────────────────────────────────────────────
-- 2-2. LIKE 와일드카드 위치
-- ────────────────────────────────────────────────────────────
-- (a) 'word%' — Index Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE title LIKE 'Post #1%';

-- (b) '%word' — Seq Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE title LIKE '%#1';

-- (c) '%word%' — Seq Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE title LIKE '%Post%';

-- → 앞 와일드카드 = B+Tree 정렬 활용 불가 → Seq Scan
-- → 실무 검색 기능은 전문 검색 (FTS) 또는 Elasticsearch 분리


-- ────────────────────────────────────────────────────────────
-- 2-3. OR 조건 — 한쪽 인덱스 없으면 / 양쪽 다 있으면 / UNION
-- ────────────────────────────────────────────────────────────
-- (a) author_id 만 — Index Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE author_id = 42 LIMIT 10;

-- (b1) OR 한쪽만 인덱스 — Seq Scan (title 인덱스 제거 후)
DROP INDEX IF EXISTS idx_post_title;
ANALYZE post;
EXPLAIN ANALYZE SELECT * FROM post WHERE author_id = 42 OR title = 'Post #1';

-- (b2) OR 양쪽 모두 인덱스 — BitmapOr
CREATE INDEX idx_post_title ON post(title);
ANALYZE post;
EXPLAIN ANALYZE SELECT * FROM post WHERE author_id = 42 OR title = 'Post #1';

-- (c) UNION 분리 — 둘 다 Index Scan (한쪽 인덱스만 있어도 OK)
EXPLAIN ANALYZE
SELECT * FROM post WHERE author_id = 42
UNION
SELECT * FROM post WHERE title = 'Post #1';

-- → 핵심: OR 는 양쪽 다 인덱스 있어야 살아남. UNION 분리는 양쪽 인덱스 독립 활용


-- ────────────────────────────────────────────────────────────
-- 2-4. 묵시적 형변환 — DB 마다 다른 동작 (PostgreSQL vs MySQL)
-- ────────────────────────────────────────────────────────────
-- (a) 같은 타입 — Index Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE code = 'CODE-42';

-- (b) varchar = integer
-- ⚠️ PostgreSQL — 묵시적 캐스팅 안 함 → 에러 (학습 흐름 끊김)
--    ERROR: operator does not exist: character varying = integer
-- ⚠️ MySQL    — 컬럼을 숫자로 캐스팅 → 인덱스 미사용 (Seq Scan). 전형적 함정
--
-- 학습 메시지: "묵시적 형변환은 인덱스를 죽이는 함정이지만, DB 마다 동작이 다르다"
-- 아래 줄 주석 해제 후 실행하면 에러 확인 가능 (PostgreSQL):
-- EXPLAIN ANALYZE SELECT * FROM post WHERE code = 42;

-- (c) integer 컬럼에 문자열 비교 — PostgreSQL 은 "상수 쪽"을 캐스팅 → 인덱스 정상 사용
--     컬럼 쪽 캐스팅이 필요한 경우만 인덱스 X.
EXPLAIN ANALYZE SELECT * FROM post WHERE author_id = '42';

-- → PostgreSQL 의 핵심: 상수 쪽 캐스팅은 OK / 컬럼 쪽 캐스팅 필요하면 인덱스 X
-- → MySQL 학습자는 둘 다 함정이 더 흔하므로 본인 DB 에서 직접 확인


-- ────────────────────────────────────────────────────────────
-- 2-5. NULL 처리
-- ────────────────────────────────────────────────────────────
-- PostgreSQL — IS NULL / IS NOT NULL 모두 인덱스 가능

-- (a) IS NULL — Index Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE deleted_at IS NULL LIMIT 100;

-- (b) IS NOT NULL — cardinality 99% 라 옵티마이저가 Seq Scan 선택 가능
EXPLAIN ANALYZE SELECT * FROM post WHERE deleted_at IS NOT NULL;

-- 해결 — 부분 인덱스 (Partial Index)
CREATE INDEX idx_post_active ON post(id) WHERE deleted_at IS NULL;
ANALYZE post;
EXPLAIN ANALYZE
SELECT * FROM post WHERE deleted_at IS NULL AND id = 42;
-- → idx_post_active 사용 + 인덱스 크기 작음


-- ────────────────────────────────────────────────────────────
-- 2-6. 부정형 — <>, NOT IN
-- ────────────────────────────────────────────────────────────
-- (a) = — Index Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE author_id = 42;

-- (b) <> — Seq Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE author_id <> 42;

-- (c) NOT IN — Seq Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE author_id NOT IN (1, 2, 3, 4, 5);

-- → 부정형 = "거의 모든 row 매칭" → Seq Scan 이 더 빠름
-- → 도메인 재설계 또는 캐시 등 별 방법


-- ────────────────────────────────────────────────────────────
-- 2-7. 측정 매트릭스 — measurements.md 에 채우기
-- ────────────────────────────────────────────────────────────
-- 위 6 케이스 모두 EXPLAIN ANALYZE 의 Execution Time 측정 후 표 채우기.
-- 본인 환경 (디스크 / 캐시 / autovacuum 상태) 에 따라 다르므로 직접 확인.
