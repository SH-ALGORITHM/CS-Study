SELECT pg_backend_pid();

-- 1-1) FOR UPDATE 관찰 ==========================
--      같은 row를 수정/잠그는 작업을 막음
BEGIN;

SELECT cash
FROM wallet
WHERE user_id = 1
  FOR UPDATE;

COMMIT;
/*
 * SELECT FOR UPDATE는 row를 읽으면서 동시에
   “이 row는 내가 수정할 예정”이라고 X-lock을 잡는다.
   다른 트랜잭션의 일반 SELECT는 가능하지만, UPDATE나 다른 FOR UPDATE는 기다린다.
 */


-- 초기화
UPDATE wallet SET cash = 1000000, version = 0 WHERE user_id = 1;
UPDATE holding SET qty = 10, version = 0 WHERE user_id = 1 AND ticker = '005930';

-- 2) 주식 도메인 데드락 재현 ==========================
BEGIN;

SELECT cash
FROM wallet
WHERE user_id = 1
  FOR UPDATE;

-- 세션 B의 자원 요청
SELECT qty
FROM holding
WHERE user_id = 1 AND ticker = '005930'
  FOR UPDATE;

/*
 * 매수/매도에서 어떤 트랜잭션은 wallet을 먼저 잡고 holding을 기다리고,
 * 다른 트랜잭션은 holding을 먼저 잡고 wallet을 기다리면 순환 대기가 생긴다.
 * 이것이 데드락이다.
 */

/*
 * 해결은 모든 거래가 항상 같은 순서로 락을 잡는 것이다.
 * 예를 들어 wallet → holding 순서 (같은 정렬 기준으로)
 */



-- 3) Redis 분산락 관찰 ==========================
SET lock:ticker:005930 "session-A" NX EX 10
-- OK

TTL lock:ticker:005930 -- TTL 확인
DEL lock:ticker:005930 -- 해제

/*
 * DB 락은 DB row를 보호한다.
 * Redis 분산락은 DB 밖의 자원, 예를 들어 같은 종목에 대한 외부 거래소 API 호출을
 *  여러 서버가 동시에 보내지 않도록 보호한다.
 * NX는 이미 락이 있으면 실패하게 하고, EX는 락 보유자가 죽어도 TTL 이후 자동 해제되게 한다.
*/
