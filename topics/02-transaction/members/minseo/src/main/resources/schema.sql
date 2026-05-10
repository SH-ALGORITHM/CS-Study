create table if not exists meeting_room_booking (
                                                  id serial primary key,
                                                  room_id int not null,
                                                  start_at timestamptz not null,
                                                  end_at timestamptz not null,
                                                  reserved_by text not null
);

TRUNCATE meeting_room_booking;
INSERT INTO meeting_room_booking (id, room_id, start_at, end_at, reserved_by) VALUES (1, 1, '2026-05-10 10:00+09', '2026-05-10 11:00+09', 'Original');
