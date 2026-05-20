# 측정 기록

-- 각 방식에 대해 50 threads × 200 attempts × 5회 평균 측정
- [05-21 04:13] s2-1 · pessimistic FOR UPDATE: 누락 0.0 / 실패 0.0 / 1053.5ms
- [05-21 04:13] s2-2 · optimistic version: 누락 0.0 / 실패 36.2 / 1254.5ms
- [05-21 04:14] s2-3 · redis SET NX EX: 누락 0.0 / 실패 194.2 / 435.2ms
