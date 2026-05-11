BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

SELECT quantity FROM stock WHERE item_id = 1;
SELECT balance FROM user_point WHERE user_id = 1;
----------------------------------------------------------------2
UPDATE stock SET quantity = 9 WHERE item_id = 1;
UPDATE user_point SET balance = 9000 WHERE user_id = 1;
COMMIT;
------------------------4

SELECT * FROM stock;
SELECT * FROM user_point;

-----------------------------------------------Lost update 상황

-----------Repeatable read 수준의 격리
BEGIN;
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;

SELECT quantity FROM stock WHERE item_id = 1;
SELECT balance FROM user_point WHERE user_id = 1;
-------------------------------------------------------2
UPDATE stock SET quantity = 9 WHERE item_id = 1;
UPDATE user_point SET balance = 9000 WHERE user_id = 1;
COMMIT;
-------------------------------------------------------4
/*
 * SQL Error [40001]: ERROR: could not serialize access due to concurrent update
Error position:
즉,   REPEATABLE READ에서는 조용히 덮어쓰지 않고 충돌을 에러로 막음.
 Lost Update가 성공 상태로 발생하지는 않음.
 * */
rollback;

-----------------------Serailizable read 수준의 격리
BEGIN;
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;

SELECT quantity FROM stock WHERE item_id = 1;
SELECT balance FROM user_point WHERE user_id = 1;
----------------------------------------------------2
UPDATE stock SET quantity = 9 WHERE item_id = 1;
UPDATE user_point SET balance = 9000 WHERE user_id = 1;
COMMIT;
------------------------------------------------------4
rollback;
/*
 * SQL Error [40001]: ERROR: could not serialize access due to concurrent update
Error position:
즉, SERIALIZABLE도 Lost Update를 조용히 허용하지 않고 트랜잭션 실패로
처리함.
 * */

---------------------------데드락 확인
BEGIN;

UPDATE user_point
SET balance = balance - 1000
WHERE user_id = 1;

-----------------------B실행 2
UPDATE stock
SET quantity = quantity - 1
WHERE item_id = 1;
------------------------- 4
/*
 *SQL Error [40P01]: ERROR: deadlock detected
Detail: Process 3612 waits for ShareLock on transaction 753; blocked by process 3586.
Process 3586 waits for ShareLock on transaction 754; blocked by process 3612.
Hint: See server log for query details.
Where: while updating tuple (0,4) in relation "stock"
*/

------------------------------------
ROLLBACK;



