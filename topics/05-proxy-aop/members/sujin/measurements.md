# 측정 기록

## STAGE 1
- [06-04 04:11] s1 · JDK Dynamic Proxy — proxy.getClass() = jdk.proxy1.$Proxy0
- [06-04 04:11] s1 · CGLIB Proxy — getClass() = stage.s1.ReportService$$EnhancerByCGLIB$$ca5d45c8 / instanceof ReportService = true

- [06-04 04:14] s1 · Spring getBean — PlainService(advice X) = stage.s1.Stage1SpringProxy$PlainService
- [06-04 04:14] s1 · Spring getBean — GuardedService(@Transactional) = stage.s1.Stage1SpringProxy$GuardedService$$SpringCGLIB$$0

- [06-04 04:18] s1 · 인터페이스 있음 + @Transactional → stage.s1.Stage1JdkVsCglib$AccountServiceImpl$$SpringCGLIB$$0 (JDK proxy=false)
- [06-04 04:18] s1 · 인터페이스 있음 + @Transactional → jdk.proxy2.$Proxy45 (JDK proxy=true)

## STAGE 1 정리 — Proxy 손으로 관찰

- JDK Dynamic Proxy 클래스명     : jdk.proxy1.$Proxy0   (인터페이스 구현, impl 아님)
- CGLIB Proxy 클래스명           : ReportService$$EnhancerByCGLIB$$...  (impl 의 자식, instanceof=true)
- Spring advice 없는 빈          : 진짜 클래스 (프록시 X)
- Spring @Transactional 빈       : ...$$SpringCGLIB$$0  (Boot 3 → SpringCGLIB 로 명칭 변경)
- 인터페이스 + 기본              : CGLIB (proxy-target-class=true 기본)
- 인터페이스 + =false 강제       : jdk.proxyN.$ProxyN (JDK)

결론: Spring 은 "끼울 advice 가 있을 때만" 프록시를 만들고, 기본은 CGLIB.
JDK 는 인터페이스 필수(+강제 설정), CGLIB 는 상속이라 인터페이스 무관.


----------

- [06-04 04:27] s2 · 순진한 @MyTransactional 함정 — 예외 후 user_role 행 수=1 (0이면 정상 / 1이면 함정 재현)
- [06-04 04:39] s2 · ThreadLocal @MyTransactional — 실패시 user_role=0 (0=롤백 성공) / 정상시 user_role=1 (1=커밋)

- [06-04 04:43] s2 · @RequireRole(@Before) — ADMIN→user 42 삭제 완료 / USER→거부(AccessDeniedException): 권한 부족: ADMIN 필요 (현재: USER)
- [06-04 04:46] s2 · @Order 양파 — 권한 거부 시 [TX] begin 미출력 / user_role(10)=0 (AuthAspect @Order(1) 이 TX 바깥)
- [06-04 04:49] s2 · Advice 5종 순서 — 정상: [Around시작]>[Before]>메서드>[AfterReturning]>[After]>[Around종료] (Around 종료가 After 뒤 = Spring 5.2.7+ 확인)
- [06-04 04:53] s2 · Pointcut 3종 — deleteUser=exec+anno+within(3) / viewUser=exec+within(2, @annotation 제외)

----------

- [06-04 04:57] s3 · 1M회 호출 — 순수=0ms / JDK=1ms / CGLIB=3ms (min/5)
- [06-04 04:57] s3 · internal/AutoProxy 빈 13개 — AnnotationAwareAspectJAutoProxyCreator 가 @Aspect 프록시 생성 주체

----------

- [06-04 05:00] s4 · self-invocation — 직접=거부: 권한 부족: ADMIN 필요 (현재: USER) / this경유=user 2 삭제 완료 / self프록시=거부: 권한 부족: ADMIN 필요 (현재: USER) / final=user 4 (final) 삭제
