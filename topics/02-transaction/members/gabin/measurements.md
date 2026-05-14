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
