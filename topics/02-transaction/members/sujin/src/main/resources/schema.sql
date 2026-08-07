DROP TABLE IF EXISTS seat;

CREATE TABLE seat (
                    concert_id BIGINT NOT NULL,
                    seat_no VARCHAR(10) NOT NULL,
                    reserved_by BIGINT NULL,
                    reserved_at TIMESTAMPTZ NULL,

                    PRIMARY KEY (concert_id, seat_no)
);

INSERT INTO seat (concert_id, seat_no)
SELECT 1, 'A' || g
FROM generate_series(1, 100) AS g;
