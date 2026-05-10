-- dirty read
begin;
set transaction isolation level read uncommitted;

-- 현재 상태 확인 (id = 1인 방의 예약자 확인)
select * from meeting_room_booking where id = 1;
-- 다시 조회
select * from meeting_room_booking where id = 1;

commit;



-- non-repeatable-read
begin;
set transaction isolation level read uncommitted;

-- 현재 상태 확인 (id = 3인 방의 예약자 확인)
select * from meeting_room_booking where id = 3;

-- 다시 조회
SELECT reserved_by FROM meeting_room_booking WHERE id = 3;
commit;

select * from meeting_room_booking where id = 3;



-- phantom read
begin;
set transaction isolation level read uncommitted;

-- 현재 예약 건수 확인
select count(*) from meeting_room_booking;

-- 다시 조회
select count(*) from meeting_room_booking;
commit;




TRUNCATE meeting_room_booking;
INSERT INTO meeting_room_booking (id, room_id, start_at, end_at, reserved_by) VALUES (1, 1, '2026-05-10 10:00+09', '2026-05-10 11:00+09', 'Original');

-- lost update - atomic update
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

-- repeatable 버전
SET TRANSACTION ISOLATION LEVEL repeatable read;

UPDATE meeting_room_booking SET reserved_by = reserved_by || '_A' WHERE id = 1;

commit;

select reserved_by from meeting_room_booking where id = 1;




TRUNCATE meeting_room_booking;
INSERT INTO meeting_room_booking (id, room_id, start_at, end_at, reserved_by) VALUES (1, 1, '2026-05-10 10:00+09', '2026-05-10 11:00+09', 'Original');

-- lost update - read-modify-write
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

SELECT reserved_by FROM meeting_room_booking WHERE id = 1;

UPDATE meeting_room_booking SET reserved_by = 'A_User' WHERE id = 1;

commit;

select reserved_by from meeting_room_booking where id = 1;
