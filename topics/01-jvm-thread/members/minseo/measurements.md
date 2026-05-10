# 측정 기록

자동 누적. 옆에 해석 메모는 직접 추가하세요.

- [05-04 03:20] s1 · race-repro: 누락 0.0 / 0.0ms
  -> 처음 스레드 수: 50 반복 : 200 
  -> 도메인 로직이 너무 단순해 망가지지 않음
- [05-04 03:21] s1 · race-repro: 누락 1.0 / 0.0ms
  -> 스레드 수: 200 반복: 50000
  -> 도장 하나가 덮어 씌여짐
  -> 값을 중복으로 false로 읽고 true를 두번 return해서 발생

- [05-04 03:40] s2 · visibility 재현 (volatile X, 빈 루프): 누락 1.0 / 0.0ms
  -> 빈 while 루프에서 CPU 캐시의 값을 계속 참조하여 메인 메모리의 변경사항을 감지하지 못함
  -> Thread.join(3000) 이후에도 isAlive가 true

- [05-05 13:35] s1 · race-repro: 누락 0.0 / 0.0ms
- [05-05 13:35] s1 · race-repro: 누락 0.0 / 0.0ms
- [05-05 13:37] s1 · race-repro: 누락 0.0 / 0.0ms
- [05-05 13:37] s1 · race-repro: 누락 0.0 / 0.0ms
- [05-05 13:38] s1 · race-repro: 누락 0.0 / 0.0ms
- [05-05 13:38] s1 · race-repro: 누락 0.0 / 0.0ms

- [05-05 14:32] s1 · race-repro: 누락 49.0 / 0.0ms CountDownLatch
- [05-05 14:42] s1 · race-repro: 누락 19.0 / 0.0ms
- [05-05 14:51] s1 · race-repro: 누락 18.0 / 0.0ms
- [05-07 17:18] s1 · race-repro: 누락 21.0 / 0.0ms

------------------------

stage3 
해결책 X 스레드 수에 따른 누락 및 시간 비교
- [05-07 21:58] s3 · thread-count-10: 평균누락 9.0 / 12.8ms
- [05-07 21:58] s3 · thread-count-50: 평균누락 34.2 / 8.8ms
- [05-07 21:58] s3 · thread-count-100: 평균누락 43.2 / 16.6ms
- [05-07 21:58] s3 · thread-count-1000: 평균누락 68.6 / 168.6ms

--------------------------
### none
- [05-07 23:53] s3 · type-nonethread-count-10: 누락 9.0 / 9.8ms
- [05-07 23:53] s3 · type-nonethread-count-50: 누락 40.8 / 10.6ms
- [05-07 23:53] s3 · type-nonethread-count-100: 누락 66.8 / 15.0ms
- [05-07 23:53] s3 · type-nonethread-count-1000: 누락 63.4 / 152.8ms

### sync
- [05-07 23:53] s3 · type-syncthread-count-10: 누락 0.0 / 5.2ms
- [05-07 23:53] s3 · type-syncthread-count-50: 누락 0.0 / 6.8ms
- [05-07 23:53] s3 · type-syncthread-count-100: 누락 0.0 / 12.4ms
- [05-07 23:53] s3 · type-syncthread-count-1000: 누락 0.0 / 196.2ms

### atomic
- [05-07 23:53] s3 · type-atomicthread-count-10: 누락 0.0 / 4.4ms
- [05-07 23:53] s3 · type-atomicthread-count-50: 누락 0.0 / 15.4ms
- [05-07 23:53] s3 · type-atomicthread-count-100: 누락 0.0 / 26.4ms
- [05-07 23:53] s3 · type-atomicthread-count-1000: 누락 0.0 / 206.6ms

-------------------------
## atomic 시간이 sync보다 느리게 나옴 왜그럴까
### sleep 제거해보기

- [05-08 00:02] s3 · type-syncthread-count-10: 누락 0.0 / 4.0ms
- [05-08 00:02] s3 · type-syncthread-count-50: 누락 0.0 / 7.4ms
- [05-08 00:02] s3 · type-syncthread-count-100: 누락 0.0 / 13.6ms
- [05-08 00:02] s3 · type-syncthread-count-1000: 누락 0.0 / 133.4ms
- 
- [05-08 00:02] s3 · type-atomicthread-count-10: 누락 0.0 / 1.8ms
- [05-08 00:02] s3 · type-atomicthread-count-50: 누락 0.0 / 8.0ms
- [05-08 00:02] s3 · type-atomicthread-count-100: 누락 0.0 / 10.2ms
- [05-08 00:02] s3 · type-atomicthread-count-1000: 누락 0.0 / 110.8ms

### sync sleep 밖으로 빼보기
- [05-08 00:06] s3 · type-nonethread-count-10: 누락 9.0 / 8.6ms
- [05-08 00:06] s3 · type-nonethread-count-50: 누락 48.4 / 8.2ms
- [05-08 00:06] s3 · type-nonethread-count-100: 누락 92.2 / 10.0ms
- [05-08 00:06] s3 · type-nonethread-count-1000: 누락 80.4 / 132.4ms

- [05-08 00:06] s3 · type-syncthread-count-10: 누락 0.0 / 2787.2ms
- [05-08 00:07] s3 · type-syncthread-count-50: 누락 0.0 / 2897.6ms
- [05-08 00:07] s3 · type-syncthread-count-100: 누락 0.0 / 3013.6ms

### 작업량 늘리기
- [05-08 00:11] s3 · type-nonethread-count-10: 누락 9.0 / 14.2ms
- [05-08 00:11] s3 · type-nonethread-count-50: 누락 45.6 / 15.8ms
- [05-08 00:11] s3 · type-nonethread-count-100: 누락 63.2 / 14.8ms
- [05-08 00:11] s3 · type-nonethread-count-1000: 누락 81.0 / 127.4ms
- 
- [05-08 00:11] s3 · type-syncthread-count-10: 누락 0.0 / 12.2ms
- [05-08 00:11] s3 · type-syncthread-count-50: 누락 0.0 / 12.2ms
- [05-08 00:11] s3 · type-syncthread-count-100: 누락 0.0 / 12.8ms
- [05-08 00:11] s3 · type-syncthread-count-1000: 누락 0.0 / 119.4ms
- 
- [05-08 00:11] s3 · type-atomicthread-count-10: 누락 0.0 / 6.4ms
- [05-08 00:11] s3 · type-atomicthread-count-50: 누락 0.0 / 8.0ms
- [05-08 00:11] s3 · type-atomicthread-count-100: 누락 0.0 / 11.2ms
- [05-08 00:11] s3 · type-atomicthread-count-1000: 누락 0.0 / 102.2ms

## STAGE 2 VISIBILITY 문제 해결
- [05-08 00:38] s2 · Visibility-Repro (No Volatile): 누락 1.0 / 3014.0ms
- [05-08 00:38] s2 · Visibility-Resolved (Volatile Applied): 누락 0.0 / 0.0ms
