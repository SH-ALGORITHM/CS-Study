# 5주차 Spring Proxy / AOP — 용어 정리

> 4 주차의 IoC / Bean 용어 정리와 같은 형식. STAGE 진행 전 또는 학습 중 막힐 때 참조.
>
> 시나리오 단어표 (12 개) 는 핵심만, 이 파일은 카테고리별 전체.

---

## 🌳 Proxy 본질 + 두 방식

| 용어 | 풀어쓰면 |
|---|---|
| **Proxy (프록시)** | 실제 객체 앞에 끼어있는 대리 객체. 메서드 호출 가로채기 가능 |
| **Proxy 패턴** | GoF 디자인 패턴. "원본 객체 접근 제어 / 부가 동작 끼우기" 위해 대리 객체 사용 |
| **JDK Dynamic Proxy** | 자바 표준 (`java.lang.reflect.Proxy`). **인터페이스 기반** — 인터페이스 필수 |
| **CGLIB** (Code Generation Library) | 런타임 바이트코드 조작으로 클래스 상속. 인터페이스 없어도 OK. Spring 6 은 `org.springframework.cglib.proxy.Enhancer` 내장 |
| **`InvocationHandler`** | JDK Proxy 의 콜백. `invoke(proxy, method, args)` 로 메서드 호출 가로채기 |
| **`MethodInterceptor`** | CGLIB 의 콜백. `intercept(obj, method, args, methodProxy)` |
| **`Enhancer`** | CGLIB 의 프록시 생성기. `setSuperclass + setCallback + create` |
| **`MethodProxy.invokeSuper`** | CGLIB 에서 원본 메서드 호출. 리플렉션보다 빠름 (FastClass) |
| **`Proxy.newProxyInstance`** | JDK Dynamic Proxy 생성. `(classLoader, interfaces, handler)` |
| **`$Proxy0` / `$Proxy1`** | JDK Proxy 클래스명 패턴 |
| **`X$$EnhancerByCGLIB$$...`** | CGLIB Proxy 클래스명 패턴. Spring 은 `X$$EnhancerBySpringCGLIB$$...` |
| **MethodHandle** | Java 7 도입 (JSR-292), Java 9+ 부터 리플렉션 대체 성능. JDK Proxy 가 Java 21 에서 빠른 이유 |

## 🎭 AOP 핵심 개념

| 용어 | 풀어쓰면 |
|---|---|
| **AOP** (Aspect-Oriented Programming) | 관점 지향 프로그래밍. 여러 클래스에 흩어진 공통 관심사를 분리 |
| **Aspect (애스펙트)** | 공통 관심사 (로깅 / 트랜잭션 / 권한) 를 모아놓은 단위. `@Aspect` 어노테이션 |
| **Advice (어드바이스)** | Aspect 가 실제로 끼워넣는 코드. 5 종 — Before / After / AfterReturning / AfterThrowing / Around |
| **JoinPoint (조인포인트)** | Advice 가 끼어들 수 있는 지점. Spring AOP 는 **메서드 호출만** 지원 (필드 / 생성자 X) |
| **Pointcut (포인트컷)** | 어느 JoinPoint 에 advice 를 끼울지 표현식으로 지정 |
| **Weaving (위빙)** | Advice 를 실제 코드에 짜넣는 과정 |
| **런타임 위빙** | Spring AOP — 컨테이너 시작 시 프록시 생성으로 위빙 |
| **컴파일 타임 위빙** | AspectJ — 컴파일러가 바이트코드에 직접 위빙. 학습 범위 밖 |
| **로드 타임 위빙** (LTW) | AspectJ — 클래스 로드 시점에 바이트코드 수정. 학습 범위 밖 |
| **공통 관심사** (Cross-cutting Concerns) | 여러 클래스 / 메서드에 흩어진 동일 패턴 (로깅 / 트랜잭션 등) |
| **횡단 관심사** | 공통 관심사의 한국어 번역 |
| **`@EnableAspectJAutoProxy`** | Spring AOP 활성화 어노테이션. Spring Boot 는 `spring-boot-starter-aop` 가 자동 활성화 |

## 💉 Advice 5 종 + ProceedingJoinPoint

| 용어 | 풀어쓰면 |
|---|---|
| **`@Before`** | 메서드 호출 직전. 인자 검증 / 로그 진입 |
| **`@After`** | 메서드 종료 후 (정상 / 예외 무관). `finally` 같은 자리 — 자원 해제 |
| **`@AfterReturning`** | 정상 종료 후. 반환값을 advice 에서 받을 수 있음 (`returning = "result"`) |
| **`@AfterThrowing`** | 예외 발생 시. 예외 객체를 받을 수 있음 (`throwing = "ex"`) |
| **`@Around`** | 가장 일반. `ProceedingJoinPoint.proceed()` 호출 전후로 자유 |
| **`ProceedingJoinPoint`** | `@Around` 에 전달되는 JoinPoint. `proceed()` 가 실제 메서드 호출 |
| **`JoinPoint`** | `@Before` / `@After` 등에 전달. `getSignature()`, `getArgs()` |
| **호출 순서** (≥ 5.2.7, 정상) | Around 시작 → Before → 메서드 → AfterReturning → After → Around 종료 |
| **호출 순서** (≥ 5.2.7, 예외) | Around 시작 → Before → 메서드 (예외) → AfterThrowing → After → Around 예외 처리 |
| **5.2.6 → 5.2.7 변경** (Issue #25186) | 5.2.6 이하 = 메서드 선언 순서 의존 (비결정적). 5.2.7 부터 우선순위 고정 `Around > Before > After > AfterReturning > AfterThrowing` |
| **on the way in / out** | 들어갈 때 = 우선순위 높은 게 먼저 / 나갈 때 = 우선순위 높은 게 가장 늦게. → Around 가 양파의 가장 바깥 |
| **`@Around` 가 가장 일반인 이유** | 나머지 4 종은 `@Around` 의 특수 케이스. `proceed()` 전후 + try/catch 자유 |

## 🎯 Pointcut 표현식

| 용어 | 풀어쓰면 |
|---|---|
| **`execution(...)`** | 메서드 시그니처 패턴. 가장 흔함 — `execution(* com.example..*Service.*(..))` |
| **`@annotation(MyAnnotation)`** | 특정 어노테이션 붙은 메서드. 의도 명확 |
| **`within(MyClass)`** | 특정 클래스 내 모든 메서드 |
| **`args(...)`** | 인자 타입 패턴 매칭 |
| **`target(...)`** | 대상 객체 타입 (프록시가 가리키는 실제 객체) |
| **`this(...)`** | 프록시 객체 타입 |
| **`bean(beanName)`** | Bean 이름으로 매칭 |
| **`..` (두 점)** | 임의 패키지 / 임의 인자. `com.example..*Service.*(..)` |
| **`*` (와일드카드)** | 임의 한 단어 |
| **`+` (서브타입 포함)** | `MyClass+` — `MyClass` 와 모든 자식 |
| **`&&` / `\|\|` / `!`** | Pointcut 조합 — AND / OR / NOT |
| **`@Pointcut`** | 재사용 가능한 pointcut 정의. `@Pointcut("execution(...)") public void any() {}` |

## 🔄 @Transactional 내부 + ThreadLocal

| 용어 | 풀어쓰면 |
|---|---|
| **`@Transactional`** | 메서드 전후로 begin / commit / rollback 자동 삽입 |
| **`TransactionInterceptor`** | `@Transactional` 의 실제 advice. `Around` 으로 동작 |
| **`PlatformTransactionManager`** | 트랜잭션 추상화. JDBC / JPA / Hibernate 별 구현체 |
| **`DataSourceTransactionManager`** | JDBC 전용 구현. HikariCP + 순수 JDBC 사용자 |
| **`TransactionSynchronizationManager`** | ThreadLocal 기반 트랜잭션 컨텍스트 관리. 현재 스레드의 Connection / 동기화 콜백 |
| **`DataSourceUtils.getConnection(ds)`** | `TransactionSynchronizationManager` 에서 conn 꺼냄. 없으면 새로 |
| **`ThreadLocal<Connection>`** | 스레드별 Connection 보관. Aspect 가 시작한 트랜잭션을 Repository 가 같은 conn 으로 받는 메커니즘 |
| **`ThreadLocal.remove()`** | 메모리 누수 방지. 스레드풀 환경에서 다음 요청에 오염되지 않도록 |
| **Propagation (전파)** | REQUIRED (기본) / REQUIRES_NEW / NESTED / SUPPORTS / MANDATORY / NEVER / NOT_SUPPORTED |
| **Isolation (격리)** | DEFAULT / READ_UNCOMMITTED / READ_COMMITTED / REPEATABLE_READ / SERIALIZABLE — 2 주차 회상 |
| **`@Transactional(readOnly = true)`** | 읽기 전용 트랜잭션. flush 생략 / 일부 DB 최적화 |
| **`@Transactional(rollbackFor = ...)`** | 기본은 RuntimeException 만 rollback. checked exception 도 rollback 하려면 명시 |
| **`@Transactional(timeout = N)`** | 트랜잭션 최대 실행 시간 (초) |

## 🔗 AOP 체이닝 + @Order

| 용어 | 풀어쓰면 |
|---|---|
| **AOP 체이닝** | 여러 Aspect 가 같은 메서드에 매칭될 때 양파 껍질처럼 겹쳐서 호출 |
| **`@Order(N)`** | Aspect 우선순위 명시. **숫자 작은 게 바깥** |
| **`Ordered.HIGHEST_PRECEDENCE`** | `Integer.MIN_VALUE` — 가장 바깥 |
| **`Ordered.LOWEST_PRECEDENCE`** | `Integer.MAX_VALUE` — 가장 안쪽 |
| **양파 껍질 구조** | 트랜잭션 (바깥) → 감사 → 측정 (안쪽) → 실제 메서드 |
| **트랜잭션이 바깥인 이유** | commit 전에 감사 / 알림이 발사되면 데이터 불일치. 트랜잭션이 모든 advice 감싸야 안전 |
| **commit 후 처리가 필요한 경우** | `@TransactionalEventListener(AFTER_COMMIT)` — 6 주차 본론 |
| **순서 불명시 시 동작** | Spring 이 임의 순서 결정 — 불확정. 면접 / 실무에서는 `@Order` 명시 권장 |

## ⚠️ 함정 + CGLIB 한계

| 용어 | 풀어쓰면 |
|---|---|
| **self-invocation** | 같은 클래스 안에서 `this.method()` 호출 → 프록시 우회 → AOP 작동 안 함 |
| **`this` vs 프록시** | `this` 는 진짜 객체. 프록시는 외부 호출만 가로챔 |
| **해결 (a) 자기 자신 주입** | `@Autowired @Lazy private Self self;` 후 `self.method()`. 동작은 함, 설계는 어색 |
| **해결 (b) ApplicationContext** | `ctx.getBean(X.class).method()` — Service Locator 패턴, 안티패턴 |
| **해결 (c) 클래스 분리** | 가장 권장. `innerMethod` 를 다른 Service 로 빼고 주입 |
| **`final` 클래스** | CGLIB 못 상속 → 부팅 실패 (`Cannot subclass final class`) |
| **`final` 메서드** | 오버라이드 불가 → WARN 로그 + advice 스킵 |
| **`private` 메서드** | 외부 호출 자체 불가 → 프록시 의미 없음 → advice 스킵 |
| **`static` 메서드** | 객체 메서드 아님 → 프록시 적용 불가 |
| **`UndeclaredThrowableException`** | JDK Proxy 에서 `InvocationHandler.invoke()` 가 선언 안 된 checked exception 던질 때 |
| **`AopContext.currentProxy()`** | self-invocation 임시 해결. `((Self) AopContext.currentProxy()).method()`. `@EnableAspectJAutoProxy(exposeProxy=true)` 필요. 설계 냄새 |

## 🏗 BeanPostProcessor (4 주차 회수)

| 용어 | 풀어쓰면 |
|---|---|
| **`BeanPostProcessor`** | Bean 생성 후처리 hook. AOP / `@Transactional` / `@Autowired` 등이 동작하는 자리 |
| **`postProcessBeforeInitialization`** | Bean 의 `@PostConstruct` 호출 전 |
| **`postProcessAfterInitialization`** | Bean 의 `@PostConstruct` 호출 후. **AOP 가 프록시로 교체하는 시점** |
| **`AnnotationAwareAspectJAutoProxyCreator`** | Spring AOP 의 핵심 `BeanPostProcessor`. `@Aspect` 클래스를 찾아서 매칭되는 Bean 을 프록시로 교체 |
| **4 주차 `internal*` 5 개** | `internalConfigurationAnnotationProcessor` / `AutowiredAnnotationProcessor` / `CommonAnnotationProcessor` / `EventListenerProcessor` / `EventListenerFactory` |
| **5 주차 추가 1 개** | `AnnotationAwareAspectJAutoProxyCreator` (또는 `InfrastructureAdvisorAutoProxyCreator`) — Bean 6 개로 증가 |
| **`Advisor`** | Pointcut + Advice 의 묶음. Spring AOP 의 내부 추상 |
| **`PointcutAdvisor`** | Pointcut 기반 Advisor |
| **`ProxyFactory`** | Spring 내부에서 프록시 생성하는 팩토리. `setTargetClass / setInterfaces / addAdvice` |

## 🌉 6 주차 브릿지 — Event

| 용어 | 풀어쓰면 |
|---|---|
| **`ApplicationEvent`** | Spring 의 이벤트 base 클래스 (Spring 4.2+ 부터는 POJO 도 가능) |
| **`ApplicationEventPublisher`** | 이벤트 발행 인터페이스. `publishEvent(event)` |
| **`@EventListener`** | 이벤트 리스너 어노테이션. 동기 (기본) |
| **`@TransactionalEventListener`** | 트랜잭션 상태 기반 리스너. `phase = AFTER_COMMIT / BEFORE_COMMIT / AFTER_ROLLBACK / AFTER_COMPLETION` |
| **`TransactionPhase`** | `BEFORE_COMMIT` / `AFTER_COMMIT` (기본) / `AFTER_ROLLBACK` / `AFTER_COMPLETION` |
| **`@Async`** | 비동기 실행. `@EventListener + @Async` 조합 — self-invocation 함정 동일 |
| **AOP vs Event** | AOP = 암묵적 가로채기 (어노테이션) / Event = 명시적 발행 (`publishEvent`) |
| **동기 vs 비동기 이벤트** | 기본 동기 (호출 스레드 블록). `@Async` 로 별 스레드 |
| **`SimpleApplicationEventMulticaster`** | 이벤트 발행자 → 리스너 분배. `setTaskExecutor` 로 비동기화 |

## ⚙️ 측정 / JVM

| 용어 | 풀어쓰면 |
|---|---|
| **`System.nanoTime()`** | 나노초 정밀도 시간. AOP 오버헤드 측정 |
| **JIT 웜업** | 첫 실행은 인터프리터, 5,000 ~ 10,000 회 호출 후 JIT 컴파일. 측정 전 웜업 필수 |
| **MethodHandle** | Java 7 (JSR-292) 도입. 코어 리플렉션 `Method.invoke` 이 MethodHandle 기반으로 재구현된 건 **Java 18** (JDK-8266571). JDK Proxy 호출 비용은 결과적으로 줄었지만 인과는 단순하지 않음 |
| **JIT 인라이닝** | HotSpot C2 컴파일러의 상시 최적화 (2000 년대 초부터). 작은 메서드를 호출 지점에 펼침. `MaxInlineSize` / `FreqInlineSize` 플래그로 제어. "Java 16+ 도입" 같은 버전 게이트 없음 |
| **결론 — JDK vs CGLIB 런타임 비용** | Java 21 기준 JIT 웜업 후 사실상 동등. 정확한 메커니즘보다 **본인 측정값**을 신뢰 |
| **FastClass** | CGLIB 가 리플렉션 우회 위해 생성하는 보조 클래스. `MethodProxy.invokeSuper` 의 속도 비결 |
| **부팅 시 비용** | CGLIB > JDK Proxy. 바이트코드 생성 + 클래스 로딩 |
| **런타임 호출 비용** | Java 21 기준 JDK ≈ CGLIB (JIT 후 동등) |
| **`ConcurrentHashMap`** | 멀티스레드 안전 Map. 호출 카운트 / 메서드별 통계에 사용 |
| **`AtomicInteger`** | 락 없이 안전한 카운터 |
| **JMH** (Java Microbenchmark Harness) | 정확한 마이크로벤치마크 도구. 학습 범위 밖이지만 측정 정확도 원할 시 |

## 🧪 학습 도구

| 용어 | 풀어쓰면 |
|---|---|
| **`@Aspect`** | Aspect 표시. `@Component` 와 함께 붙여야 Bean 등록 + Aspect 적용 |
| **`spring-boot-starter-aop`** | Spring AOP 의존성 묶음. AspectJ Weaver 포함 |
| **`@EnableAspectJAutoProxy`** | AOP 활성화. Spring Boot 는 starter 가 자동 활성화 |
| **`@EnableAspectJAutoProxy(proxyTargetClass = true)`** | CGLIB 강제. Spring Boot 2.0+ 기본값 |
| **`@EnableAspectJAutoProxy(exposeProxy = true)`** | `AopContext.currentProxy()` 활성화 — self-invocation 임시 해결 |
| **`spring.aop.proxy-target-class`** | 프로퍼티. true = CGLIB 강제 / false = JDK 강제 |
| **`AnnotationConfigApplicationContext`** | 4 주차에서 다룬 컨테이너. STAGE 1 에서 그대로 사용 |
| **`SpringApplication.run()`** | Spring Boot 시작점. AOP starter 가 있으면 자동 활성화 |
| **`@Target(ElementType.METHOD)`** | 자작 어노테이션 위치 제한 — 메서드에만 |
| **`@Retention(RetentionPolicy.RUNTIME)`** | 자작 어노테이션이 런타임까지 살아있어야 AOP 가 인식 |

---

## ★ STAGE 1 진입 관문 (입으로 답)

1. **JDK Dynamic Proxy vs CGLIB 차이** — 인터페이스 유무가 결정. JDK = 인터페이스 기반 / CGLIB = 클래스 상속
2. **`@Transactional` 의 begin / commit 이 끼어드는 메커니즘** — `TransactionInterceptor` 가 `Around` advice 로 메서드 호출 가로채기. ThreadLocal 로 Connection 공유
3. **self-invocation 이 안 먹는 이유** — `this` 는 진짜 객체. 프록시는 외부 호출만 가로챔. 해결 = 클래스 분리 권장

## ★ STAGE 2-1 진입 관문 (5 주차 가장 중요)

1. **순진한 `@MyTransactional` 의 함정** — Aspect 의 conn 과 Repository 의 conn 이 다름 → 같은 트랜잭션 아님
2. **ThreadLocal 의 자리** — `TX_CONN.set/get/remove` 로 Aspect 가 시작한 conn 을 Repository 가 같은 걸로 받음
3. **Spring 의 `TransactionSynchronizationManager`** — 위 ThreadLocal 의 실무 추상화
