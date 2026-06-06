-- ============================================================
-- 00. 셋업 — Post + Comment 100 만 row seed
-- ============================================================
-- 7 주차 게시판 도메인 그대로. 100 만 게시글 + 1000 만 댓글.
-- 실행 시간: 약 30 초 ~ 2 분 (환경에 따라)

DROP TABLE IF EXISTS comment;
DROP TABLE IF EXISTS post;
DROP TABLE IF EXISTS author;

CREATE TABLE author (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL
);

CREATE TABLE post (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    author_id BIGINT NOT NULL,
    code VARCHAR(20),                                                -- STAGE 2-4 묶시적 형변환용
    deleted_at TIMESTAMP NULL,                                       -- STAGE 2-5 NULL 처리용
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE comment (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 1000 명 작성자
INSERT INTO author (name)
SELECT 'Author #' || i FROM generate_series(1, 1000) AS i;

-- 100 만 게시글 — 약 30 초
INSERT INTO post (title, content, author_id, code, deleted_at, created_at)
SELECT
    'Post #' || i,
    'Content of post ' || i,
    (i % 1000) + 1,                                                  -- 1000 명 작성자 균등 분배
    'CODE-' || i,
    CASE WHEN i % 100 = 0 THEN NOW() ELSE NULL END,                  -- 1% 만 삭제 표시
    NOW() - (i || ' seconds')::interval
FROM generate_series(1, 1000000) AS i;

-- 1000 만 댓글 — 약 1 분 (필요 시 100 만으로 줄여도 OK)
INSERT INTO comment (post_id, content, created_at)
SELECT
    (random() * 999999 + 1)::bigint,                                 -- 무작위 게시글에 분배
    'Comment ' || i,
    NOW() - (i || ' seconds')::interval
FROM generate_series(1, 10000000) AS i;

-- 통계 갱신 — 옵티마이저가 정확한 plan 선택하도록 필수
ANALYZE author;
ANALYZE post;
ANALYZE comment;

-- 크기 확인
SELECT
    pg_size_pretty(pg_total_relation_size('post')) AS post_size,
    pg_size_pretty(pg_total_relation_size('comment')) AS comment_size;
