# 측정 기록

## [STAGE 2] 각 방식에 대해 50 threads × 200 attempts × 5회 평균 측정
- [05-21 04:13] s2-1 · pessimistic FOR UPDATE: 누락 0.0 / 실패 0.0 / 1053.5ms
- [05-21 04:13] s2-2 · optimistic version: 누락 0.0 / 실패 36.2 / 1254.5ms
- [05-21 04:14] s2-3 · redis SET NX EX: 누락 0.0 / 실패 194.2 / 435.2ms

## [STAGE 3]
- [05-21 04:24] s3 · low - 100 rows / pessimistic FOR UPDATE: 누락 0.0 / 실패 0.0 / 279.4ms
- [05-21 04:24] s3 · low - 100 rows / optimistic version: 누락 0.0 / 실패 0.0 / 172.6ms
- [05-21 04:24] s3 · low - 100 rows / redis SET NX EX: 누락 0.0 / 실패 0.0 / 1262.5ms
- [05-21 04:24] s3 · medium - 10 rows / pessimistic FOR UPDATE: 누락 0.0 / 실패 0.0 / 232.8ms
- [05-21 04:24] s3 · medium - 10 rows / optimistic version: 누락 0.0 / 실패 25.4 / 581.1ms
- [05-21 04:24] s3 · medium - 10 rows / redis SET NX EX: 누락 0.0 / 실패 140.2 / 481.3ms
- [05-21 04:24] s3 · high - 1 row / pessimistic FOR UPDATE: 누락 0.0 / 실패 0.0 / 1290.7ms
- [05-21 04:24] s3 · high - 1 row / optimistic version: 누락 0.0 / 실패 34.8 / 1567.5ms
- [05-21 04:24] s3 · high - 1 row / redis SET NX EX: 누락 0.0 / 실패 193.2 / 321.3ms

- low : 100 rows 
      - 요청이 여러 row로 분산되어 충돌이 거의 없다. 
      - 낙관락은 lock wait 없이 version 확인만 하므로 가장 빠르게 나왔다.
- medium : 10 rows
      - 충돌이 생기기 시작하면서 낙관락은 일부 요청이 maxRetries를 초과했다. 
      - Redis 분산락은 fail-fast 전략이라 lock을 못 잡은 요청이 많이 실패했다.
- high : 1 row
      - 모든 요청이 같은 wallet/holding row에 몰린다. 
      - 비관락은 느리지만 200건을 모두 직렬 처리했고, 
      - 낙관락은 재시도 비용 때문에 더 느려지면서 일부 실패가 발생했다. 
      - Redis 분산락은 대부분 lock 획득에 실패해 응답시간은 짧지만 성공 수가 매우 적다.
