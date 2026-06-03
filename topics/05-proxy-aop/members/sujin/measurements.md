# 측정 기록

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

