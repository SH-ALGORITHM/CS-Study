# 측정 기록

자동 누적. 옆에 해석 메모는 직접 추가하세요.


- [05-04 14:16] s1 · race 재현: 초과 2.0 / 4.7ms
  - 1,000개의 동시 예약 요청에서 기대 성공 수는 100건이지만, 실제 성공 수는 102건으로 측정되었다.
  - 동시에 여러 스레드가 `availableSeats > 0` 조건을 통과한 뒤 각각 감소 연산을 수행하면서 좌석 수보다 더 많은 예약이 성공한 race condition이다.
- [05-04 14:57] s2 · stop flag visibility (volatile 없음): 위반 1.0 / 6006.0ms
  - main 스레드가 soldOut = true로 매진 신호를 보냈지만 판매 스레드가 변경된 값을 보지 못해 5초 join 대기 후에도 계속 실행되었다.
  - volatile이 없으면 스레드 간 값 변경이 즉시 보인다는 보장이 없다는 것을 확인하였다.
- [05-08 05:25] s3 · Unsafe thr=10: 초과예약 542.0 / 0.1ms
- [05-08 05:25] s3 · Synchronized thr=10: 초과예약 0.0 / 0.3ms
- [05-08 05:25] s3 · Atomic thr=10: 초과예약 0.0 / 0.6ms
- [05-08 05:25] s3 · Unsafe thr=50: 초과예약 19592.4 / 2.0ms
- [05-08 05:25] s3 · Synchronized thr=50: 초과예약 0.0 / 1.7ms
- [05-08 05:25] s3 · Atomic thr=50: 초과예약 0.0 / 5.5ms
- [05-08 05:25] s3 · Unsafe thr=100: 초과예약 40000.0 / 4.4ms
- [05-08 05:25] s3 · Synchronized thr=100: 초과예약 0.0 / 4.6ms
- [05-08 05:25] s3 · Atomic thr=100: 초과예약 0.0 / 11.4ms
- [05-08 05:25] s3 · Unsafe thr=1000: 초과예약 1585.2 / 24.7ms
- [05-08 05:25] s3 · Synchronized thr=1000: 초과예약 0.0 / 25.8ms
- [05-08 05:25] s3 · Atomic thr=1000: 초과예약 0.0 / 38.1ms
- [05-08 05:35] s4 · Virtual-Unsafe thr=10: 초과예약 2465.6 / 0.3ms
- [05-08 05:35] s4 · Virtual-Synchronized thr=10: 초과예약 0.0 / 0.4ms
- [05-08 05:35] s4 · Virtual-Atomic thr=10: 초과예약 0.0 / 0.7ms
- [05-08 05:35] s4 · Virtual-Unsafe thr=50: 초과예약 18540.0 / 2.0ms
- [05-08 05:35] s4 · Virtual-Synchronized thr=50: 초과예약 0.0 / 2.6ms
- [05-08 05:35] s4 · Virtual-Atomic thr=50: 초과예약 0.0 / 6.3ms
- [05-08 05:35] s4 · Virtual-Unsafe thr=100: 초과예약 42721.4 / 4.8ms
- [05-08 05:35] s4 · Virtual-Synchronized thr=100: 초과예약 0.0 / 4.5ms
- [05-08 05:35] s4 · Virtual-Atomic thr=100: 초과예약 0.0 / 11.1ms
- [05-08 05:35] s4 · Virtual-Unsafe thr=1000: 초과예약 460566.8 / 48.6ms
- [05-08 05:35] s4 · Virtual-Synchronized thr=1000: 초과예약 0.0 / 48.4ms
- [05-08 05:35] s4 · Virtual-Atomic thr=1000: 초과예약 0.0 / 123.5ms
