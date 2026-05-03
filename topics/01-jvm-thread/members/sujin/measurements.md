# 측정 기록

자동 누적. 옆에 해석 메모는 직접 추가하세요.

- [05-04 00:21] s2 · visibility 재현 (volatile X, 빈 루프): 누락 1.0 / 0.0ms
  → 누락 1 = isAlive() true = visibility 재현 성공.
    plain boolean은 JMM happens-before 없어 워커가 캐시값 false를 계속 읽음.
    println/sleep 넣었을 땐 잘 멈췄는데, 그건 동기화 부수효과 때문이었음.
