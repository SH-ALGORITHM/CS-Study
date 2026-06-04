# 측정 기록

자동 누적. 옆에 해석 메모는 직접 추가하세요.

- [06-04 05:45] s1 · JDK Dynamic Proxy 손 작성: proxy=jdk.proxy2.$Proxy2 / GreeterImpl 상속 여부=false
- [06-04 05:45] s1 · CGLIB Proxy 손 작성: proxy=stage.Stage1$Counter$$EnhancerByCGLIB$$9bf4a6a2 / Counter 상속 여부=true
- [06-04 05:46] s1 · Spring AOP 프록시 확인: txService=stage.Stage1$TxService$$SpringCGLIB$$0 / plainService=stage.Stage1$PlainService


- [06-04 06:15] s2-1 · 순진한 @MyTransactional 함정: id1=9500.00 / id2=10000.00 / Aspect conn 과 Repository conn 별개
- [06-04 06:36] s2-1 · ThreadLocal @MyTransactional 해결: id1=10000.00 / id2=10000.00 / 같은 conn 공유 후 rollback
- [06-04 07:07] s2-2 · @Order 양파 껍질: TX(1) > AUDIT(2) > TIMED(3) 순서 확인
- [06-04 07:36] s2-3 · Pointcut 표현식: execution / @annotation / within 매칭 확인
- [06-04 07:58] s2-4 · Advice 5 종 호출 순서: Around 가 가장 바깥 / 정상과 예외 순서 확인
- [06-04 08:24] s2-5 · @Cached 자작 어노테이션 적용: first=146ms / second=0ms / queryCount=2


- [06-04 08:54] s3-1 · AOP 오버헤드 1M 회: plain=15ms / advised=9509ms / overhead=63293.3 %
- [06-04 09:16] s3-2 · JDK vs CGLIB 1M 회: pure=26ms / jdk=40ms / cglib=30ms
- [06-04 09:25] s3-3 · getClass 매트릭스: TX 있는 Bean 은 CGLIB 프록시 클래스명 출력
- [06-04 10:41] -> 에러 수정 s3-4 · BeanPostProcessor 확인: internalOrAutoProxy=13 / autoProxy=true


- [06-04 09:56] s4-1 · self-invocation 함정: outerMethod TX 적용 / innerMethod self 호출은 프록시 우회
- [06-04 10:16] s4-2 · self-invocation 해결: self injection 동작 / 클래스 분리 권장
- [06-04 10:34] s4-3 · CGLIB 한계: final / private / static 은 프록시 적용 한계 확인
