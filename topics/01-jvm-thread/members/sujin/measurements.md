# 측정 기록

자동 누적. 옆에 해석 메모는 직접 추가하세요.

- [05-04 00:21] s2 · visibility 재현 (volatile X, 빈 루프): 누락 1.0 / 0.0ms
    누락 1 = isAlive() true = visibility 재현 성공.
    plain boolean은 JMM의 happens-before 관계가 없어 
    워커 스레드가 캐시된 false 값을 계속 읽음.
    println/sleep 추가 시 정상 종료된 이유는
    내부 동기화(synchronized, memory barrier)로 인해 가시성이 우연히 보장됐기 때문


- [05-04 01:20] s1 · race 재현 (count++ RMW): 누락 5.0 / 17.3ms
    누락 5 = race condition 재현 성공
    락 없이 50개 스레드가 동시에 RMW(Read-Modify-Write) 수행 시
    약 0.5% 수준의 증가 연산 누락 발생
    두 스레드가 같은 값을 읽고 각각 +1 수행 후
    동일한 값으로 덮어쓰면서 한 번의 증가가 유실됨 (lost update)
