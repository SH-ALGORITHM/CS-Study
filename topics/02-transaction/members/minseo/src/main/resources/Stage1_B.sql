-- dirty read
begin;
-- 예약자를 'hacker'로 바꿈 (아직 커밋 X)
update meeting_room_booking set reserved_by = 'Hacker' where id = 1;

rollback;



-- none-repeatable-read
begin;

-- 예약자를 'hacker'로 바꿈
update meeting_room_booking set reserved_by = 'Hacker' where id = 3;
commit;



-- phantom-read
begin;

insert into meeting_room_booking (room_id, start_at, end_at, reserved_by) VALUES (1, '2026-05-10 15:00+09', '2026-05-10 16:00+09', 'Newbie');
commit;




-- lost update - atomic update
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

-- repeatable 버전
SET TRANSACTION ISOLATION LEVEL repeatable read;

UPDATE meeting_room_booking SET reserved_by = reserved_by || '_B' WHERE id = 1;

COMMIT;



-- lost update - read-modify-write
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

SELECT reserved_by FROM meeting_room_booking WHERE id = 1;

UPDATE meeting_room_booking SET reserved_by = 'B_User' WHERE id = 1;

commit;
