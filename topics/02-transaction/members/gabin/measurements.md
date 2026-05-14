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
- [05-14 09:38] s2-1 · checkout READ_COMMITTED: 누락 1.0 / 실패 398.0 / 3909.5ms
- [05-14 09:43] s2-1 · checkout READ_COMMITTED: 누락 190.0 / 실패 0.0 / 3604.4ms
> === STAGE 2-1: 장바구니 결제 race 재현 (READ_COMMITTED + RMW) === <br>
시도: 200, 성공: 200, 실패: 0
생성된 주문: 200
최종 재고: 190 (기대값: 0)
Lost Update: 190
응답시간: 3604.4ms
- [05-14 10:18] s2-2 · helper checkout READ_COMMITTED: 누락 194.0 / 실패 0.0 / 6436.1ms
> === STAGE 2-2: TransactionHelper 사용 (READ_COMMITTED + RMW) === <br>
시도: 200, 성공: 200, 실패: 0
생성된 주문: 200
최종 재고: 194 (기대값: 0)
Lost Update: 194
응답시간: 6436.1ms
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
