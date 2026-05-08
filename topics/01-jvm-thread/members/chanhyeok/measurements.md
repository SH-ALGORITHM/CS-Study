# 측정 기록

자동 누적. 옆에 해석 메모는 직접 추가하세요.

- [05-04 01:28] s1 · race 재현 (200스레드 x 1000회): 누락 5.0 / 10.0ms
- [05-04 01:28] s1 · race 재현 (200스레드 x 1000회): 누락 4.0 / 9.0ms
- [05-04 02:29] s2 · stop flag visibility (volatile 없음): 누락 1.0 / 5107.0ms
- [05-04 14:22] s2 · stop flag visibility (volatile 없음): 누락 1.0 / 5109.0ms
- [05-05 14:42] s1 · race 재현 (200스레드 x 1000회): 누락 9.0 / 10.0ms
- [05-05 14:45] s2 · stop flag visibility (volatile 없음): 누락 1.0 / 5106.0ms
- [05-07 23:34] s1 · race 재현 (200스레드 x 1000회): 누락 0.0 / 9.0ms
- [05-08 00:12] s3 · 해결책 없음: 누락 1.8 / 6.1ms
- [05-08 00:12] s3 · synchronized: 누락 0.0 / 6.5ms
- [05-08 00:12] s3 · AtomicInteger: 누락 0.0 / 5.3ms
- [05-08 00:17] s3 · 해결책 없음: 누락 3.2 / 6.0ms
- [05-08 00:17] s3 · synchronized: 누락 0.0 / 4.8ms
- [05-08 00:17] s3 · AtomicInteger: 누락 0.0 / 4.7ms
- [05-08 00:26] s3 · 해결책 없음: 누락 1.0 / 8.5ms
- [05-08 00:26] s3 · synchronized: 누락 0.0 / 7.6ms
- [05-08 00:26] s3 · AtomicInteger: 누락 0.0 / 6.5ms
- [05-08 00:27] s3 · 해결책 없음: 누락 1.8 / 8.0ms
- [05-08 00:27] s3 · synchronized: 누락 0.0 / 7.3ms
- [05-08 00:27] s3 · AtomicInteger: 누락 0.0 / 7.8ms
- [05-08 00:35] s4 · Virtual Threads (10000개): 누락 10.0 / 33.0ms
- [05-08 00:39] s4 · Virtual Threads (10000개): 누락 0.0 / 78.0ms
- [05-08 00:41] s4 · Virtual Threads (10000개): 누락 0.0 / 66.0ms
- [05-08 00:41] s4 · Virtual Threads (10000개): 누락 0.0 / 72.0ms
- [05-08 00:44] s4 · Virtual Threads (10000개): 누락 0.0 / 71.0ms
- [05-08 00:49] s4 · Virtual Threads (10000개): 누락 0.0 / 69.0ms
- [05-08 00:49] s4 · Virtual Threads (10000개): 누락 0.0 / 76.0ms
- [05-08 00:49] s4 · Virtual Threads (10000개): 누락 0.0 / 72.0ms
- [05-08 00:50] s4 · Virtual Threads (10000개): 누락 15.0 / 133.0ms
- [05-08 00:53] s4 · Virtual Threads (10000개): 누락 0.0 / 80.0ms
