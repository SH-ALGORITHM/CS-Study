-- STAGE 2-2. 계층 분리용 DDL
-- SchemaBootstrap (@PostConstruct) 가 부팅 시점에 이 파일을 읽어서 실행한다.
-- 'to' 는 PostgreSQL 예약어이므로 컬럼명을 'to_address' 로 둔다.
CREATE TABLE IF NOT EXISTS notification_log (
    id          BIGSERIAL    PRIMARY KEY,
    to_address  VARCHAR(255) NOT NULL,
    message     TEXT         NOT NULL,
    channel     VARCHAR(32)  NOT NULL,
    sent_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);
