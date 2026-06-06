# 5주차 예시 코드 — 감사 로그 / 트랜잭션 자작 (Spring Proxy + AOP)

scenario.md 의 12 개 도메인과 **별개로** 만든 참고 코드입니다.
4 주차의 `NotificationService` 가 다형성 + Bean 라이프사이클 학습이었다면, **이번엔 같은 객체의 메서드 호출을 프록시로 가로채는 버전**.

> ⚠️ **베끼지 마시고 본인 도메인으로 변환하세요.** "이런 식으로 흘러가는구나" 감을 잡는 용도.

## 4 주차와 무엇이 같고 다른가

| | 4 주차 Notification | 5 주차 Audit / Transactional |
|---|---|---|
| 풀려고 하는 문제 | 객체 생성 / 의존성 연결 | 메서드 호출 자체를 가로채기 |
| 도구 | `@Component` / `@Bean` / `@Autowired` / `@Qualifier` | `@Aspect` / `@Around` / `Pointcut` / `Advice 5 종` |
| 학습 포인트 | Bean 라이프사이클 + DI 방식 + 순환 참조 | Proxy 본질 + `@Transactional` 분해 + self-invocation |
| 컨테이너가 반환하는 객체 | 진짜 클래스 | 프록시 (`X$$EnhancerBySpringCGLIB$$...`) |
| 면접 직결 | 생성자 주입 / 순환 참조 / `@Qualifier` vs `@Primary` | `@Transactional` 안 먹는 3 가지 / JDK vs CGLIB |

핵심: 4 주차 STAGE 5 의 `proxyBeanMethods` (CGLIB 프록시) 가 5 주차 본론 (`@Transactional`, `@Aspect`) 의 동일 메커니즘.

## 폴더 구조

```
example/
├── README.md                              # 지금 이 파일
├── build.gradle                           # Spring Boot 3.x + spring-boot-starter-aop + H2
├── src/main/
│   ├── java/
│   │   ├── infra/                              # 측정 도구
│   │   │   └── MeasurementLog.java
│   │   ├── domain/                             # 자작 어노테이션 + Aspect + Service + Repository
│   │   │   ├── Audited.java                    # 자작 어노테이션 — 감사 로그
│   │   │   ├── AuditAspect.java                # @Around 로 감사 기록
│   │   │   ├── MyTransactional.java            # 자작 어노테이션 — 트랜잭션
│   │   │   ├── NaiveTransactionalAspect.java   # 순진한 버전 (Step 1 함정)
│   │   │   ├── MyTransactionalAspect.java      # ThreadLocal 버전 (Step 3 해결)
│   │   │   ├── OrderService.java               # 적용 대상
│   │   │   └── OrderRepository.java            # JDBC 작업
│   │   └── stage/
│   │       ├── s1/                             # STAGE 1: 손 관찰
│   │       │   ├── Stage1JdkProxy.java         # JDK Dynamic Proxy 손 작성
│   │       │   ├── Stage1CglibProxy.java       # CGLIB 손 작성
│   │       │   └── Stage1SpringProxy.java      # Spring AOP 의 프록시 확인
│   │       ├── s2/                             # STAGE 2: @Transactional 분해 + @Aspect 자작
│   │       │   ├── Stage2_1_NaiveTrap.java     # 순진한 버전 함정 재현
│   │       │   ├── Stage2_1_ThreadLocal.java   # ThreadLocal 해결
│   │       │   ├── Stage2_2_OrderChaining.java # @Order advice 안-밖
│   │       │   ├── Stage2_3_Pointcut.java      # Pointcut 3 가지
│   │       │   ├── Stage2_4_FiveAdvice.java    # Advice 5 종 호출 순서 (≥5.2.7)
│   │       │   └── Stage2_5_Audited.java       # @Audited 도메인 적용
│   │       ├── s3/                             # STAGE 3: 측정
│   │       │   ├── Stage3_1_Overhead.java      # AOP 적용 전후 응답 시간
│   │       │   ├── Stage3_2_JdkVsCglib.java    # 1M 회 호출 비교
│   │       │   ├── Stage3_3_GetClass.java      # getClass() 매트릭스
│   │       │   └── Stage3_4_BeanPostProcessors.java
│   │       └── s4/                             # STAGE 4: self-invocation + CGLIB 한계
│   │           ├── Stage4_1_SelfInvocation.java
│   │           ├── Stage4_2_Resolve.java
│   │           └── Stage4_3_CglibLimits.java
│   └── resources/
│       └── application.properties              # H2 인메모리 DB
```

## 실행 방법

```bash
cd topics/05-proxy-aop/example

# STAGE 1-1 JDK Dynamic Proxy
./gradlew run -PmainClass=stage.s1.Stage1JdkProxy

# STAGE 1-2 CGLIB
./gradlew run -PmainClass=stage.s1.Stage1CglibProxy

# STAGE 2-1 순진한 버전 함정 재현
./gradlew run -PmainClass=stage.s2.Stage2_1_NaiveTrap

# STAGE 2-1 ThreadLocal 해결
./gradlew run -PmainClass=stage.s2.Stage2_1_ThreadLocal

# ... 나머지도 동일 패턴
```

## 핵심 학습 흐름

1. **STAGE 1** — Spring AOP 가 자동으로 해주는 일을 손으로 짜본다 (JDK Proxy + CGLIB)
2. **STAGE 2-1** ★ — `@MyTransactional` 의 순진한 버전이 트랜잭션을 어떻게 깨뜨리는지 직접 재현 → ThreadLocal 로 해결
3. **STAGE 2-2 ~ 5** — `@Order` advice 안-밖 + Pointcut 3 종 + Advice 5 종 (Spring ≥5.2.7 순서 검증) + `@Audited` 자작
4. **STAGE 3** — 측정 (오버헤드 / JDK vs CGLIB / `getClass()` / BeanPostProcessor)
5. **STAGE 4** — self-invocation 함정 + final / private / static 한계

> **STAGE 2-1 이 5 주차 가장 중요한 학습**. 순진 → 함정 → ThreadLocal 3 단계로 직접 겪어야 `TransactionSynchronizationManager` 의 본질을 이해.
