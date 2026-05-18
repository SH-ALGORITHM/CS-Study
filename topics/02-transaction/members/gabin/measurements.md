# 측정 기록

자동 누적. 옆에 해석 메모는 직접 추가하세요.

- [05-14 09:33] s2-1 · checkout READ_COMMITTED: 누락 1.0 / 실패 0.0 / 204.8ms
> lost update 1건 발생. <br>
> === STAGE 2-1: 장바구니 결제 race 재현 (READ_COMMITTED + RMW) ===<br>
성공: 2, 실패: 0
생성된 주문: 2
최종 재고: 9 (기대값: 8)
고객 101 포인트: 9000, 고객 102 포인트: 9000
Lost Update: 1
응답시간: 204.8ms

기본값으로 세션 2개에서 LOST UPDATE 일어나는지 확인함. 
즉, 트랜잭션에서 주문/ 포인트는 2건 반영됐지만 재고 차감은 1건만 반영된 정합성 문제가 발생. 


- [05-14 09:43] s2-1 · checkout READ_COMMITTED: 누락 190.0 / 실패 0.0 / 3604.4ms
> === STAGE 2-1: 장바구니 결제 race 재현 (READ_COMMITTED + RMW) === <br>
시도: 200, 성공: 200, 실패: 0
생성된 주문: 200
최종 재고: 190 (기대값: 0)
Lost Update: 190
응답시간: 3604.4ms

기댓값은 재고 차감 200개인데, 10개만 차감된 상황. -> 이를 통해 READ COMMITTED에서 RMW 패턴을 쓰면 DB가 충돌을 에러로 알려주지 않고 
마지막 UPDATE들이 서로 덮어쓰며 LOST UPDATE가 누적된다는 것을 파악 

- [05-14 10:18] s2-2 · helper checkout READ_COMMITTED: 누락 194.0 / 실패 0.0 / 6436.1ms
> === STAGE 2-2: TransactionHelper 사용 (READ_COMMITTED + RMW) === <br>
시도: 200, 성공: 200, 실패: 0
생성된 주문: 200
최종 재고: 194 (기대값: 0)
Lost Update: 194
응답시간: 6436.1ms

반복되는 코드를 helper를 사용함으로써 줄임. 트랜잭션헬퍼 사용해도 역시나 LOST UPDATE는 발생함. -> helper는 commit/rollback/connection 정리를 자동화할 뿐
이것을 통해 추후 @Transactional을 사용해도 동시성 충돌이 해결되지 않음을 파악할 수 있음. 


- [05-14 10:55] s3 · READ_COMMITTED: 누락 194.2 / 실패 0.0 / 2871.6ms
- [05-14 10:55] s3 · REPEATABLE_READ: 누락 0.0 / 실패 195.8 / 953.1ms
- [05-14 10:55] s3 · SERIALIZABLE: 누락 0.0 / 실패 195.4 / 1318.5ms
> READ_COMMITTED 측정 완료: Lost Update 194.2, 실패 0.0, 응답시간 2871.6ms
REPEATABLE_READ 측정 완료: Lost Update 0.0, 실패 195.8, 응답시간 953.1ms
SERIALIZABLE 측정 완료: Lost Update 0.0, 실패 195.4, 응답시간 1318.5ms

| 격리 수준 | Lost Update 평균 | 실패 평균 | 응답시간 평균(ms) |
|---|---:|---:|---:|
| READ_COMMITTED | 194.2 | 0.0 | 2871.6 |
| REPEATABLE_READ | 0.0 | 195.8 | 953.1 |
| SERIALIZABLE | 0.0 | 195.4 | 1318.5 |
> READ COMMITTED는 빠르게 성공 처리되지만 잘못된 값이 저장되고 REPEATABLE은 DB가 충돌을 감지해서 덮어쓰기를 허용하지 않고 트랜잭션을 실패시킴. LOST UPDATE는 없어졌지만 성공하지 못한 트랜잭션이 많아짐
> SERIALIZABLE또한 잘못된 덮어쓰기를 막고 실패 처리. 

RR이 RC보다 빠르다고 생각했는데, 이를 RR이 더 빠르다고 보면 안 된다는 것을 배움. RC는 대부분 끝까지 진행하고 커밋하는 반면 RR과 SR은 충돌난 트랜잭션이 중간에 빠르게 실패된 것임
