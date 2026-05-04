# 측정 기록

### S1 로그 
- [05-04 10:56] s1 · 이중 환불 race 재현: 누락 0.0 / 0.0ms
- [05-04 10:56] s1 · 이중 환불 race 재현: 누락 0.0 / 0.0ms   
- [05-04 11:25] s1 · 이중 환불 race 재현: 누락 0.0 / 0.0ms
- [05-04 11:26] s1 · 이중 환불 race 재현: 누락 0.0 / 0.0ms   
> 시간 측정 안 하고 있었음. 클로드 피드백 반영하여, 추후 s3,s4 단계 로그메시지와 명확하게 구분하기 위해 메서드 명 변경
- [05-04 11:31] s1 · baseline-noSync(excessRefunds): 누락 0.0 / 11.0ms
- [05-04 11:31] s1 · baseline-noSync(excessRefunds): 누락 0.0 / 10.0ms    
> 환불 조건 확인 후 스레드가 끼어들 시간을 안 줘서 race가 발생하지 않음을 깨달음
- [05-04 11:50] s1 · baseline-noSync(excessRefunds): 누락 199.0 / 37.0ms   
- [05-04 11:50] s1 · baseline-noSync(excessRefunds): 누락 199.0 / 32.0ms   
- [05-04 18:31] s1 · baseline-noSync(excessRefunds): 누락 199.0 / 39.0ms
> 환불 조건 확인 후 환불처리 하는 사이에 시간을 벌려 줌.
- [05-04 18:31] s1 · baseline-noSync(excessRefunds): 누락 0.0 / 33.0ms
- [05-04 18:33] s1 · baseline-noSync(excessRefunds): 누락 999.0 / 48.0ms 
- [05-04 19:21] s1 · baseline-noSync(excessRefunds): 누락 999.0 / 53.0ms -> 시간 텀 없이 해보려고 시도
- [05-04 19:36] s1 · baseline-noSync(excessRefunds): 누락 999.0 / 34.0ms
- [05-04 19:39] s1 · baseline-noSync(excessRefunds): 누락 999.0 / 40.0ms
- [05-04 20:04] s1 · baseline-noSync(excessRefunds) without sleep(): 누락 0.0 / 2.0ms
- [05-04 20:08] s1 · baseline-noSync-noSleep(): 누락 0.0 / 6.0ms
- [05-04 20:09] s1 · baseline-noSync-noSleep(): 누락 0.0 / 497.0ms 
> 시간을 안 주고 스레드 풀 크기만을 변경해서 조정 가능한지 비교하기 위해 시도 해보았음. <br>
- [05-04 20:13] s1 · baseline-noSync-sleep10ms-pool8-rounds200: 누락 1400.0 / 3088.0ms 
> sleep 켜니까 race 매 라운드마다 발견. == 8개 스레드 전부 같은 조건문을 통과했다는 것. <br><br>
> 결론 : sleep 없이는 발견할 가능성이 낮으나 race는 확률적으로 가려질 뿐 잠재적으로 존재하는 결함임을 깨달음.

### S2 로그 
- [05-04 22:49] s2 · stop flag visibility (volatile 없음): 누락 1.0 / 6028.0ms 
  > 5초 타임아웃이 끝났는데도 refundProcessor가 살아 있음. 즉, main이 refundCutoffReached = true로 변경한 것을 refundProcessor가 못 본 것으로 visibility 위반 재현에 성공한 것. <br>
  > RefundProcessor가 빈 루프 진입 → JIT가 1초 동안 워밍업하면서 flag 변수가 안 변한다고 판단하여 while(true)로 최적화 된 것이거나 또는 CPU 캐시에 false로 지정된 것<br>
  > main에서 변경된 결과는 메인 스레드의 메모리에만 반영된 것이고 이것이 환불프로세서에서는 캐시나 JIT 때문에 계속 false로 보아서 무한 루프 돔.
- [05-04 23:04] s2 · stop flag visibility (volatile 없음): 누락 0.0 / 26.0ms
- [05-04 23:05] s2 · stop flag visibility (volatile 없음): 누락 0.0 / 23.0ms
  > JIT 워밍업 시간을 줄이면 visibility 위반이 안 보이는 것을 확인할 수 있었음. 
