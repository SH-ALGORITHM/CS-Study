DROP TABLE IF EXISTS user_role;
DROP TABLE IF EXISTS grant_log;

CREATE TABLE user_role (
                         user_id BIGINT      NOT NULL,
                         role    VARCHAR(20) NOT NULL
);

CREATE TABLE grant_log (
                         user_id BIGINT      NOT NULL,
                         role    VARCHAR(20) NOT NULL,
                         note    VARCHAR(100)
);
