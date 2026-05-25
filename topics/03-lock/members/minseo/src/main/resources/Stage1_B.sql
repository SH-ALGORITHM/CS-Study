-- FOR UPDATE 실습

BEGIN;

SELECT * FROM seat WHERE id = 1;   --안막음

SELECT * FROM seat WHERE id = 1 FOR UPDATE; --막음

UPDATE seat SET reserved_by = 'UserB' WHERE id = 1;

SELECT * FROM seat WHERE id = 1 FOR SHARE;

commit;

-- 데드락

BEGIN;

UPDATE seat SET reserved_by = 'UserB' WHERE id = 2;

UPDATE seat SET reserved_by = 'UserB' WHERE id = 1;

COMMIT;
