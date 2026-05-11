## 장바구니 결제 도메인
### 시나리오 
> 사용자가 장바구니 상품을 결제할 때, 상품 재고를 1개 차감하고 사용자 포인트를 1000점 차감
> 동시에 같은 사용자의 결제 요청이 2번 들어오면, 둘 다 같은 재고와 포인트 값을 읽고
> 계산해서 마지막 UPDATE가 앞선 UPDATE를 덮어써 Lost Update가 발생할 수 있는 상황 가정

### 가정 
- stock.quantity = 10
- user_point.balance = 10000
라고 할 때, 결제 1번 당 재고 -1 / 포인트 -1000 이 차감된다. 
- 결제 2번이 일어나면 기댓값은 재고 8 / 잔액 8000
- 여기서 차감 가격은 애플리케이션 기준으로 계산된 값으로 수정하는 것이므로 DB에서 계산하는 것이 아니라 값을 수정해주는 식으로 작성함. 
- 즉 애플리케이션이 quantity=10, balance=10000을 읽고 계산했다고 가정
- 이런 상황에서 실제값은 재고 9 / 잔액 9000 인 것을 격리 수준 별로 확인하고자 한다.

- 더불어 A에서 stock을 수정하며 락을 획득하고 B에서는 point를 수정하며 락을 획득했을때 이후 각각 서로의 것을 수정할 때, 락이 풀리기까지 무한 대기하는 경우가 발생. 
- DB에서는 이를 막기 위해 데드락 실패처리를 할 것으로 예상. 이를 확인하기 


#### UPDATE 방식 비교
##### atomic update
우선 atomic update 수준으로 set 재고 = 재고 -1로 계산해서 값을 수정하는 방식으로 구현하면
두 트랜잭션이 순차적으로 반영되어 최종적으로 재고가 8이 됨.
> PostGreSQL row lock 덕분에 lost update가 발생하지 않은 것임을 확인 가능

##### read-modify-write
애플리케이션에서 quantity - 1 계산 후 DB 값 변경인 상황. 
UPDATE stock SET quantity = 9 WHERE item_id = 1;
- 두 트랜잭션이 같은 quantity=10을 읽고 같은 quantity=9를 저장
- 최종 quantity=9
> Lost Update 발생

### Lost Update
#### READ COMMITED 격리 수준 
SELECT * FROM stock;<br>
SELECT * FROM user_point; 했을 시, 기댓값이 아니라 재고 9 / 잔액 9000이 출력되는 것을 확인함. => Lost Update 발생 
> 해당 구조로 코드를 작성하면 값 변경 후 commit을 했으니 다른 세션에서도 변경된 값을 읽고 기댓값처럼 재고가 차감될 것이라는 가정과는 달리
동시성 문제로 인해 같은 값을 읽어서 올바르게 차감되지 않음을 확인함. 
> 이를 통해 commit이 변경사항을 확정하는 기능이지, 동시성 충돌을 알아서 해결해주는 기능이 아님을 깨달음 


#### REPEATABLE READ 


#### SERIALIZABLE 


## 격리 수준별 Lost Update 관찰

| 격리 수준 | 결과 | 트레이드오프 |
  |---|---|---|
| READ COMMITTED | Lost Update 발생 | 빠르게 성공하지만 잘못된 값이
  조용히 저장됨 |
| REPEATABLE READ | concurrent update 에러 발생 | 잘못된 덮어쓰기는
  막지만 트랜잭션 실패 처리 필요 |
| SERIALIZABLE | serialization failure 발생 | 더 강한 격리지만 실패
  시 재시도 로직 필요 |

### DeadLock
- 세션 A: stock 먼저 수정 후 user_point 수정 시도
- 세션 B: user_point 먼저 수정 후 stock 수정 시도
- 서로 상대가 잡은 row lock을 기다리다가 deadlock detected 발생

> SQL Error [40P01]: ERROR: deadlock detected
Detail: Process 3612 waits for ShareLock on transaction 753; blocked by process 3586.
Process 3586 waits for ShareLock on transaction 754; blocked by process 3612.
Hint: See server log for query details.
Where: while updating tuple (0,4) in relation "stock"
> 
라는 에러가 발생하는 것을 확인할 수 있다. 



