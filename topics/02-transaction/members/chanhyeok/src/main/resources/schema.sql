-- 학습 포인트 : Phantom Read + Lost Update (시간대 충돌)
-- 같은 객실 / 같은 날짜에 두 트랜잭션이 동시에 INSERT 시도하면 이중 예약

DROP TABLE IF EXISTS room_booking CASCADE;
DROP TABLE IF EXISTS room CASCADE;

CREATE TABLE IF NOT EXISTS room(
  room_no INTEGER PRIMARY KEY,
  room_type VARCHAR(20) NOT NULL DEFAULT 'standard'
);

CREATE TABLE IF NOT EXISTS room_booking(
  id BIGSERIAL PRIMARY KEY,
  room_no INTEGER NOT NULL REFERENCES room(room_no),
  check_in DATE NOT NULL,
  check_out DATE NOT NULL,
  guest_name VARCHAR(100) NOT NULL
);

-- room_no로 자주 조회하니 인덱스
CREATE INDEX IF NOT EXISTS idx_room_booking_room on room_booking (room_no);

TRUNCATE room_booking RESTART IDENTITY;

-- 호텔 객실 마스터 데이터
INSERT INTO room (room_no, room_type) values
      (101, 'standard'),
      (102, 'standard'),
      (103, 'deluxe')
ON CONFLICT (room_no) DO NOTHING;

-- 체크
SELECT * FROM room;
SELECT * FROM room_booking;
