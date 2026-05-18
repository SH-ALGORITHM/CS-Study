BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

SELECT quantity FROM stock WHERE item_id = 1;
SELECT balance FROM user_point WHERE user_id = 1;
-----------------------------------------------------1
UPDATE stock SET quantity = 9 WHERE item_id = 1;
UPDATE user_point SET balance = 9000 WHERE user_id = 1;
COMMIT;
------------------------------------------------------3

SELECT * FROM stock;
SELECT * FROM user_point;

-----------------------------------------------Lost update 상황
--Repeatable read 수준의 격리

BEGIN;
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;

SELECT quantity FROM stock WHERE item_id = 1;
SELECT balance FROM user_point WHERE user_id = 1;
-----------------------------------------------------1
UPDATE stock SET quantity = 9 WHERE item_id = 1;
UPDATE user_point SET balance = 9000 WHERE user_id = 1;
COMMIT;
-----------------------------------------------------3
-----------------------Serailizable read 수준의 격리
BEGIN;
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;

SELECT quantity FROM stock WHERE item_id = 1;
SELECT balance FROM user_point WHERE user_id = 1;
----------------------------------------------------1
UPDATE stock SET quantity = 9 WHERE item_id = 1;
UPDATE user_point SET balance = 9000 WHERE user_id = 1;
COMMIT;
----------------------------------------------------3


----------------------------데드락 확인

BEGIN;

UPDATE stock
SET quantity = quantity - 1
WHERE item_id = 1;

------------------A먼저 실행  1

UPDATE user_point
SET balance = balance - 1000
WHERE user_id = 1;
----------------------------3

ROLLBACK;

