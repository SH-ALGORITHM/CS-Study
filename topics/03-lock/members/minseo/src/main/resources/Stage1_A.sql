-- FOR UPDATE 실습

BEGIN;

SELECT * FROM seat WHERE id = 1 FOR UPDATE;

UPDATE seat SET reserved_by = 'UserA' WHERE id = 1;

COMMIT;


-- 데드락

BEGIN;

UPDATE seat SET reserved_by = 'UserA' WHERE id = 1;

UPDATE seat SET reserved_by = 'UserA' WHERE id = 2;

COMMIT;


-- REDIS

-- SET lock:seat:1 "UserA" NX EX 10

-- SET lock:seat:1 "UserB" NX EX 10

--  10초 기다린 후 다시 시도
--  SET lock:seat:1 "UserB" NX EX 10




SELECT
  pid,
  locktype,
  relation::regclass AS table_name,
  mode,
  granted
FROM pg_locks
WHERE relation IS NOT NULL
  AND relation::regclass::text = 'seat'
ORDER BY pid;
