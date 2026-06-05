# 5주차 — 스프링이 메서드 호출을 가로채는 메커니즘 (Proxy + AOP)

이번 주제: 4 주차에 IoC 컨테이너가 "객체 생성 / 의존성 연결" 책임을 가져갔다. 5 주차는 컨테이너가 **메서드 호출 자체까지 가로채서** begin / commit / 로그 / 권한 / 캐싱을 끼워넣는 메커니즘을 다룬다. `@Transactional` 한 줄이 어떻게 트랜잭션을 시작 / 종료하는지, 그 안에 어떤 객체가 끼어있는지 직접 본다.

5 가지 학습 축:
- JDK Dynamic Proxy vs CGLIB — 인터페이스 유무로 갈리는 두 방식. 손으로 직접 작성
- `@Transactional` 분해 — begin / commit / rollback 을 누가 어떻게 끼우는가
- `@Aspect` 자작 — 로깅 / 측정 / 감사 / 권한 직접 짜기
- self-invocation 함정 — `this.method()` 가 프록시 우회. 면접 직결
- AOP 적용 시점 — `BeanPostProcessor` (4 주차 `internal*` 5 개와 동일 메커니즘)

---

## 우선 알아둬야 할 단어 (시작 전 1 분)

| 단어 | 풀어쓰면 |
|---|---|
| **Proxy (프록시)** | 실제 객체 앞에 끼어있는 대리 객체. 메서드 호출을 가로채서 전후로 다른 코드 끼워넣기 가능 |
| **Aspect (애스펙트)** | 여러 클래스에 흩어진 공통 관심사 (로깅 / 트랜잭션 / 권한) 를 모아놓은 단위 |
| **Advice (어드바이스)** | Aspect 가 실제로 끼워넣는 코드. 5 종 — Before / After / AfterReturning / AfterThrowing / Around |
| **JoinPoint (조인포인트)** | 끼어들 수 있는 지점. Spring AOP 는 메서드 호출 시점만 지원 (필드 접근 / 생성자 X) |
| **Pointcut (포인트컷)** | 어느 JoinPoint 에 끼울지 표현식으로 지정 — `execution(* com.example..*Service.*(..))` 등 |
| **Weaving (위빙)** | Advice 를 실제 코드에 짜넣는 과정. Spring AOP = 런타임 위빙 (프록시 생성) |
| **JDK Dynamic Proxy** | 자바 표준. **인터페이스 기반** — 인터페이스가 있어야 사용 가능 |
| **CGLIB** (Code Generation Library) | 런타임 바이트코드 조작. **클래스 상속** 으로 프록시 — 인터페이스 없어도 OK |
| **BeanPostProcessor** | Bean 생성 후처리 hook. AOP 가 동작하는 자리. 4 주차에서 본 `internal*` 5 개와 같은 메커니즘 |
| **`@Transactional`** | begin / commit / rollback 을 메서드 전후로 자동 삽입. 가장 흔한 AOP 사례 |
| **self-invocation** | 같은 클래스 안에서 `this.method()` 호출 → 프록시 우회 → AOP 작동 안 함. 면접 단골 |
| **`@EventListener`** | AOP 와 결 비슷한 6 주차 브릿지. 명시적 이벤트 발행 / 리스너 |

> 📚 더 깊은 용어 (5 종 advice / pointcut 표현식 / proxy 생성 시점 / CGLIB 한계 등) — [`terms.md`](terms.md) 참고. 4 주차와 같은 형식, 카테고리별 정리.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 0. 개념 숙지 (도메인 무관, 모두 동일)
━━━━━━━━━━━━━━━━━━━━━━━━━━

## 필수 개념 (AI 와 대화하며 숙지)

### Proxy 패턴 본질
1. **Proxy 패턴이 풀려는 문제** — 원래 객체의 메서드 호출 전후로 코드 (로그 / 트랜잭션 / 권한) 를 끼워넣고 싶은데 원래 코드를 수정하지 않으려면? → 앞에 대리 객체를 둔다
2. **JDK Dynamic Proxy** — `Proxy.newProxyInstance(classLoader, interfaces, InvocationHandler)`. **인터페이스 필수**. 인터페이스의 모든 메서드를 동적으로 구현하는 객체 생성
3. **CGLIB** — `Enhancer.setSuperclass(class) + setCallback(MethodInterceptor)`. **클래스 상속**. 인터페이스 없어도 OK. 단 `final` 클래스 / `final` 메서드 / `private` 메서드는 못 가로챔

### Spring AOP 와 프록시
4. **Spring AOP 가 프록시를 선택하는 규칙** — 기본은 JDK (인터페이스 있으면) / CGLIB (없으면). Spring Boot 2.0+ 부터 기본 CGLIB
5. **`ctx.getBean(X.class).getClass()`** — 진짜 `X` 가 아니라 `X$EnhancerBySpringCGLIB$$...` 또는 `$Proxy123` 가 나옴. 컨테이너가 프록시를 대신 반환

### Advice 5 종 + Pointcut
6. **`@Around` 가 모든 advice 의 상위** — `ProceedingJoinPoint.proceed()` 호출 전후로 원하는 코드. 나머지 4 종은 `@Around` 의 특수 케이스
7. **Pointcut 표현식 3 가지** — `execution(* com.example..*Service.*(..))` (패키지 + 메서드 패턴) / `@annotation(MyAnnotation)` (특정 어노테이션 붙은 메서드) / `within(MyClass)` (특정 클래스 내 모든 메서드)

### `@Transactional` 분해
8. **`@Transactional` 의 실제 동작** — `TransactionInterceptor` 가 메서드 호출 전에 `PlatformTransactionManager.getTransaction()` 호출 (begin) → 메서드 실행 → 정상 종료 시 `commit()` / 예외 시 `rollback()`
9. **트랜잭션이 시작되는 객체** = 프록시. 컨테이너가 반환하는 `TransferService` 는 프록시이고, 프록시가 `TransactionInterceptor` 를 호출
10. **ThreadLocal 의 자리** — Aspect 에서 시작한 트랜잭션 Connection 을 Repository 가 같은 conn 으로 받는 메커니즘 = `TransactionSynchronizationManager` (ThreadLocal 기반). 이게 없으면 Aspect 의 commit 과 Repository 의 UPDATE 가 다른 트랜잭션 — STAGE 2-1 에서 직접 확인

### self-invocation 함정 (면접 직결)
11. **`this.method()` 호출이 안 먹는 이유** — `this` 는 진짜 객체 (프록시 X). 프록시는 외부에서 들어온 호출만 가로챔. 같은 클래스 안 메서드 호출은 프록시를 거치지 않음
12. **해결 3 가지** — (a) 자기 자신 주입 (`@Autowired private SelfService self;` 후 `self.method()`) (b) `ApplicationContext.getBean()` 으로 자기 조회 (c) 메서드를 다른 클래스로 분리
13. **CGLIB 한계** — `final` 클래스 / `final` 메서드 / `private` 메서드는 프록시 못 만듦 (상속 / 오버라이드 불가). 정적 메서드도 X

### AOP 적용 시점 (4 주차 브릿지)
14. **`AnnotationAwareAspectJAutoProxyCreator`** — Spring AOP 가 자동 등록하는 `BeanPostProcessor`. 4 주차의 `internalAutowiredAnnotationProcessor` 와 동일 메커니즘 — Bean 생성 후 가공
15. **언제 프록시로 교체되나** — Bean 생성 직후 (`postProcessAfterInitialization`). 컨테이너가 원래 객체 대신 프록시를 등록 → 외부에서는 진짜 객체 못 봄

## 자기 검증 (입으로 답할 수 있어야 STAGE 1 시작)

**★ 관문 — 이 3 개는 입으로 답해야 STAGE 1 진입**
- [ ] ★ JDK Dynamic Proxy vs CGLIB 차이 — 인터페이스 유무가 어떻게 결정하는가
- [ ] ★ `@Transactional` 의 begin / commit 이 끼어드는 메커니즘 1 분 본인 말로
- [ ] ★ self-invocation 이 왜 작동 안 하는가 + 해결 방법 1 개

**보너스 — STAGE 1 진행하면서 답할 수 있게**
- [ ] Advice 5 종 + 언제 어느 것? (`@Around` 가 가장 일반, 나머지는 특수 케이스)
- [ ] Pointcut 표현식 3 가지 본인 예
- [ ] 4 주차의 `internal*` BeanPostProcessor 5 개 + 5 주차의 `AspectJAutoProxyCreator` 가 같은 메커니즘인 이유
- [ ] `proxyBeanMethods` (4 주차 STAGE 5-1) 와 `@Transactional` 의 프록시가 동일한 CGLIB 인 이유
- [ ] `@Transactional` 이 안 먹는 3 가지 — self-invocation / private / final
- [ ] `@EventListener` 와 AOP 의 결 비슷한 점 / 다른 점 (6 주차 예고)


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 1. 본인 도메인 선택 — 5 주차에 맞게 (공통 관심사 자연 + AOP 적용 가능)
━━━━━━━━━━━━━━━━━━━━━━━━━━

5 주차 학습 포인트 (**Proxy / Advice / Pointcut / self-invocation**) 는 **메서드 전후로 공통 관심사를 끼워넣을 자리가 자연스러운 도메인** 에서 잘 드러난다. 4 주차 도메인 (다형성 중심) 과 결이 다르므로 새 도메인 권장.

## 옵션 — 4 주차 도메인 그대로 vs 새 도메인

| 옵션 | 권장 대상 | 흐름 |
|---|---|---|
| **A. 새 도메인 선택** | 5 주차 학습 본격 | STEP 1 후보표에서 공통관심사 ★★★ 도메인 (감사 로그 / 측정 / 캐싱 등) 선택 |
| **B. 4 주차 도메인 그대로 + AOP 끼워넣기** | 도메인 새로 짜기 부담스러운 사람 | 4 주차 결제 PG / 알림 등에 `@Audited` / `@Timed` 어노테이션 자작 후 끼움 |
| **C. 혼합** | 가장 무난 | STAGE 1 ~ 2 까지 공통 학습 도메인 (예: 감사 로그) → STAGE 3 ~ 4 부터 본인 4 주차 도메인 |

**모두 STAGE 1 (손으로 JDK Proxy + CGLIB 짜기) 는 공통.** 본인 도메인 무관.

## 후보 도메인 + 적합도 (12 개 — 7 명이 1 개씩 + 여유 5)

| # | 도메인 | 공통관심사 자연 | 측정 가능 | 면접 가치 | 메모 |
|---|---|---|---|---|---|
| 1 | **감사 로그** (`audit`) | ★★★ | ★★★ | ★★★ | `@Audited` 자작. 누가 / 언제 / 무엇을 / 결과. 가장 정석 |
| 2 | **실행 시간 측정** (`metric`) | ★★★ | ★★★ | ★★ | `@Timed` 자작. AOP 오버헤드 측정에 직결 |
| 3 | **로깅** (`logging`) | ★★★ | ★★ | ★★ | 메서드 진입 / 종료 자동 로그. 입문자용 |
| 4 | **권한 검증** (`authorization`) | ★★★ | ★★ | ★★★ | `@PreAuthorize` 모방. Spring Security 의 본질 |
| 5 | **캐싱** (`cache`) | ★★★ | ★★★ | ★★★ | `@Cacheable` 모방. 두 번째 호출 시간 차로 명확한 측정 |
| 6 | **재시도** (`retry`) | ★★★ | ★★ | ★★ | `@Retryable` 자작 + exponential backoff |
| 7 | **Rate Limiting** (`rate_limit`) | ★★★ | ★★ | ★★ | 초당 N 회. 토큰 버킷 / 슬라이딩 윈도우 |
| 8 | **분산락 AOP** (`distributed_lock`) | ★★★ | ★★ | ★★★ | 3 주차 SETNX → `@DistributedLock` 어노테이션화 |
| 9 | **트랜잭션 자작** (`my_transactional`) | ★★★ | ★★ | ★★★ | `@MyTransactional` 자작 — `@Transactional` 의 실제 구현 |
| 10 | **API 응답 표준화** (`api_envelope`) | ★★ | ★ | ★ | Around advice 로 응답 wrapping. 단순 |
| 11 | **이벤트 발행 AOP** (`event_publish`) | ★★ | ★★ | ★★ | 메서드 종료 후 자동 이벤트 발행. **6 주차 직결** |
| 12 | **호출 카운트 / 통계** (`call_stats`) | ★★ | ★★★ | ★ | 메서드별 호출 횟수 + 평균 시간. 단순한 시작용 |

> **공통관심사 ★★★ 조건** = 메서드 전후로 끼워넣을 코드가 명확하고 (begin/commit, 로그, 캐시 조회/저장 등) 여러 메서드에 동일 패턴이 반복됨. AOP 의 본질이 잘 드러남.

## 도메인별 예상 시나리오 한 줄

| # | 한 줄 시나리오 |
|---|---|
| 1 | `@Audited` 붙은 메서드는 호출 전후로 (사용자 ID / 메서드 / 인자 / 결과 / 실행 시간) 을 자동 기록. Around advice |
| 2 | `@Timed` 붙은 메서드의 평균 / 최대 실행 시간을 자동 수집. nanoTime + ConcurrentHashMap |
| 3 | `@Loggable` 붙은 메서드는 진입 시 `[ENTER] method(arg1, arg2)`, 종료 시 `[EXIT] method = result (Xms)` 자동 로그 |
| 4 | `@RequireRole("ADMIN")` 붙은 메서드는 호출 전 현재 사용자 권한 검사. 미충족 시 예외 |
| 5 | `@Cached(ttl=60)` 붙은 메서드는 첫 호출 결과를 캐시. 60 초 안에 같은 인자로 호출 시 캐시 반환 (메서드 실행 X) |
| 6 | `@Retryable(max=3)` 붙은 메서드가 예외 발생 시 3 회 자동 재시도. backoff 100ms 지수 증가 |
| 7 | `@RateLimit(perSecond=10)` 붙은 메서드는 초당 10 회 초과 호출 시 즉시 거부. 토큰 버킷 |
| 8 | 3 주차 송금 메서드에 `@DistributedLock(key="user:#{userId}")` 붙이면 자동으로 Redis SETNX + finally unlock |
| 9 | `@MyTransactional` 붙은 메서드는 자동으로 `conn.setAutoCommit(false)` → 정상 종료 시 `commit()` / 예외 시 `rollback()` |
| 10 | 컨트롤러 메서드 반환값을 `ApiResponse<T>` 로 자동 wrapping. 예외 시 `ErrorResponse` 로 |
| 11 | `OrderService.placeOrder()` 메서드 종료 후 자동으로 `OrderPlacedEvent` 발행. 6 주차 EventListener 가 받음 |
| 12 | 모든 `@Service` 의 모든 public 메서드를 호출 카운트. JMX / actuator 로 노출 |

## 학습자 프로필별 추천

| 본인 상황 | 추천 도메인 |
|---|---|
| AOP 처음 / 입문자 | **3 로깅** 또는 **12 호출 카운트** — 메서드 진입 / 종료 println 부터 |
| `@Transactional` 의 실제 동작 깊이 학습 | **9 트랜잭션 자작** — 면접 최강. 직접 짜보면 self-invocation 함정 자연 |
| 면접 가치 최대화 | **1 감사 로그** / **4 권한 검증** / **5 캐싱** / **9 트랜잭션 자작** |
| 측정 / 통계 / 모니터링 관심 | **2 실행 시간 측정** / **12 호출 카운트** — AOP 오버헤드 자체도 측정 가능 |
| 3 주차 분산락 도메인 그대로 쓰기 | **8 분산락 AOP** — `@DistributedLock` 추출 → 3 주차 코드 가 한 줄로 |
| 4 주차 도메인 그대로 + AOP 끼우기 | **옵션 B** — 4 주차 결제 PG / 알림 에 `@Audited` / `@Timed` 끼움 |
| 6 주차 (이벤트) 자연스러운 브릿지 | **11 이벤트 발행 AOP** — 메서드 종료 후 자동 이벤트 발행 |
| 5 가지 advice 다 다뤄보기 | **9 트랜잭션 자작** — Around / AfterThrowing / AfterReturning 다 등장 |

## 4 주차 도메인이 약한 이유

4 주차에 다형성 중심 도메인 (결제 PG / 알림 발송) 했던 사람이 그대로 가면:
- 메서드 전후로 끼워넣을 공통 관심사가 약함 → AOP 학습 포인트가 안 보임
- 다중 구현체 + Strategy 패턴은 4 주차에 이미 다룸 → 5 주차 색깔 안 남

→ 4 주차 다형성 도메인 출신은 **1 감사 로그** / **5 캐싱** / **9 트랜잭션 자작** 중 새로 선택 권장. 본인 4 주차 도메인은 AOP 끼울 자리 (예: 결제 메서드에 `@Audited` 끼움) 정도로만 활용.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 2. 클래스 구조 매핑
━━━━━━━━━━━━━━━━━━━━━━━━━━

도메인별 추천 클래스 구조. **본인 어노테이션 + Aspect + 적용 대상 Service** 3 종 세트.

| 도메인 | 자작 어노테이션 | Aspect 클래스 | 적용 대상 |
|---|---|---|---|
| 1 감사 로그 | `@Audited` | `AuditAspect` | `OrderService.placeOrder()` 등 |
| 2 실행 시간 측정 | `@Timed` | `TimingAspect` | 모든 `@Service.public *` |
| 3 로깅 | `@Loggable` | `LoggingAspect` | 본인 도메인 Service |
| 4 권한 검증 | `@RequireRole("ADMIN")` | `AuthAspect` | 관리자 전용 메서드 |
| 5 캐싱 | `@Cached(ttl=60)` | `CacheAspect` | 무거운 조회 메서드 |
| 6 재시도 | `@Retryable(max=3)` | `RetryAspect` | 외부 API 호출 메서드 |
| 7 Rate Limiting | `@RateLimit(perSecond=10)` | `RateLimitAspect` | 컨트롤러 메서드 |
| 8 분산락 AOP | `@DistributedLock(key="...")` | `LockAspect` | 송금 / 결제 메서드 |
| 9 트랜잭션 자작 | `@MyTransactional` | `MyTransactionalAspect` | 본인 도메인 Service |
| 10 API 응답 표준화 | `@ApiEnvelope` | `EnvelopeAspect` | 컨트롤러 메서드 |
| 11 이벤트 발행 AOP | `@PublishEvent(OrderPlacedEvent.class)` | `EventPublishAspect` | `OrderService.placeOrder()` |
| 12 호출 카운트 | `@CountCall` | `CountAspect` | 모든 `@Service.*` |

## 공통 — STAGE 1 손 작성 (모두 동일)

JDK Dynamic Proxy + CGLIB 둘 다 손으로 짜본다. Spring AOP 안 쓰고:

```java
// JDK Dynamic Proxy 손 작성
public interface Greeter { String greet(String name); }
public class GreeterImpl implements Greeter {
    public String greet(String name) { return "hello " + name; }
}

Greeter real = new GreeterImpl();
Greeter proxy = (Greeter) Proxy.newProxyInstance(
    Greeter.class.getClassLoader(),
    new Class[]{Greeter.class},
    (p, method, args) -> {
        System.out.println("[before] " + method.getName());
        Object result = method.invoke(real, args);
        System.out.println("[after] " + method.getName() + " = " + result);
        return result;
    }
);
proxy.greet("world");   // 자동 로그 + 결과
```

```java
// CGLIB 손 작성 (인터페이스 없는 경우)
// import 는 Spring 내장 — net.sf.cglib 직접 추가하지 말 것 (충돌)
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;

public class Counter {                       // 인터페이스 없음
    public int next() { return 42; }
}

Enhancer enhancer = new Enhancer();
enhancer.setSuperclass(Counter.class);
enhancer.setCallback((MethodInterceptor) (obj, method, args, methodProxy) -> {
    System.out.println("[before] " + method.getName());
    Object result = methodProxy.invokeSuper(obj, args);
    System.out.println("[after] " + method.getName() + " = " + result);
    return result;
});
Counter proxy = (Counter) enhancer.create();
proxy.next();                                // 자동 로그 + 결과
```

> 핵심: Spring AOP 의 `@Aspect` 는 위 두 코드 중 하나를 자동으로 생성해주는 추상화. STAGE 1 에서 손으로 짜본 후 STAGE 2 에서 Spring AOP 와 같은 동작 확인.

## measurements.md 형식 (1, 2, 3, 4 주차와 일관)

자동 누적 형식 그대로:
```
- [05-XX 14:00] s1 · JDK Dynamic Proxy 손 작성 (관찰)
- [05-XX 22:00] s1 · CGLIB Proxy 손 작성 — getClass() = Counter$$EnhancerByCGLIB$$...
- [05-XX 22:30] s1 · ctx.getBean(TransferService.class).getClass() — 진짜가 아닌 프록시
- [05-XX 22:00] s2 · @Transactional 분해 — begin / commit 호출 위치 println 확인
- [05-XX 22:15] s2 · @MyTransactional 순진한 버전 함정 재현 — 예외 후 from 차감 그대로 남음
- [05-XX 22:30] s2 · ThreadLocal 적용 후 from 차감 같이 롤백 확인
- [05-XX 22:45] s2 · Advice 5 종 출력 — Around 종료가 After 앞인가 뒤인가 (≥5.2.7 → 뒤가 정답)
- [05-XX 23:00] s2 · @Order 양파 껍질 — TX 가 가장 바깥인지 출력으로 확인
- [05-XX 23:15] s2 · 본인 도메인에 @Audited / @Timed 끼움 — 호출 전후 자동 로그
- [05-XX 23:00] s3 · AOP 적용 전 / 후 응답 시간 — 순수 Xms / AOP Yms / 오버헤드 N%
- [05-XX 23:30] s3 · JDK vs CGLIB 1M 회 호출 시간 비교
- [05-XX 22:00] s4 · self-invocation 재현 — this.tx() 실패 / proxy.tx() 성공
- [05-XX 22:30] s4 · final 메서드 / private 메서드 CGLIB 한계 확인
```


━━━━━━━━━━━━━━━━━━━━━━━━━━
환경
━━━━━━━━━━━━━━━━━━━━━━━━━━
- Java 21
- **Spring Boot 3.x** (Spring 6.x) — `spring-boot-starter-aop` 필수
- STAGE 1 의 JDK Dynamic Proxy 는 자바 표준 (`java.lang.reflect.Proxy`)
- STAGE 1 의 CGLIB 는 Spring 이 내장한 `org.springframework.cglib.proxy.Enhancer` 사용 (별도 의존성 불필요)
- 측정용: `System.nanoTime()`, `AtomicInteger`, `ConcurrentHashMap`
- (선택) 3 주차 docker-compose Redis — `@DistributedLock` 학습자

## build.gradle 추가

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.0'
    id 'io.spring.dependency-management' version '1.1.4'
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter'
    implementation 'org.springframework.boot:spring-boot-starter-aop'

    // @Transactional 자작 비교용 (선택):
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    implementation 'org.postgresql:postgresql'

    // @DistributedLock AOP 학습자 (선택):
    implementation 'io.lettuce:lettuce-core:6.3.0.RELEASE'
}
```

> STAGE 1 (순수 JDK Proxy + CGLIB 손 작성) 은 Spring 안 써도 됨. STAGE 2 부터 `spring-boot-starter-aop`.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STAGE 진행 가이드
━━━━━━━━━━━━━━━━━━━━━━━━━━

## 예상 학습 시간 (한 주 기준)

| 단계 | 시간 | 비고 |
|---|---|---|
| STAGE 1 (JDK Proxy + CGLIB 손 작성 + getBean 프록시 확인) | 2 ~ 3 시간 | **화요일까지 (필수)** |
| **STAGE 2-1 (`@Transactional` 분해 — 순진 → 함정 → ThreadLocal)** ★ | **2 ~ 3 시간** | **목요일까지 (필수)**. 5 주차 가장 중요한 학습 |
| STAGE 2-2 (AOP 체이닝 + `@Order` 양파 껍질) | 1 시간 | advice 호출 순서 직접 확인 |
| STAGE 2-3 ~ 2-5 (Pointcut + Advice 5 종 + 본인 도메인) | 3 ~ 4 시간 | 본인 도메인 어노테이션 자작 |
| STAGE 3 (AOP 오버헤드 / JDK vs CGLIB / BeanPostProcessor 측정) | 2 ~ 3 시간 | 5 케이스 측정 + 해석 |
| STAGE 4 (self-invocation + final / private 한계) | 2 ~ 3 시간 | 면접 직결 |
| **합계** | **12 ~ 17 시간** | |
| STAGE 5 보너스 (`@EventListener` — 6 주차 브릿지) | 30 ~ 60 분 | 여유 시 |

**배분**:
- 직장인 (평일 저녁 2 시간 × 5 + 주말 8 시간) — 충분
- 학생 (주말 풀타임 2 일) — 충분
- 부담스러우면 **STAGE 2-1 (`@Transactional` 분해) + STAGE 4 (self-invocation) 가 면접 최강** — 시간 부족 시 STAGE 3 측정을 짧게

### [화 11:00 — Draft PR 마감 + 겪기 발표] — STAGE 1

> 5 주차는 **STAGE 1 (손 관찰) 까지 화요일 분량**. JDK Proxy + CGLIB 둘 다 손으로 짜본 후 `ctx.getBean().getClass()` 가 본인 클래스가 아닌 이유까지 확인하면 화요일 발표는 충분. STAGE 2 (`@Transactional` 분해) 부터는 목요일까지.

#### ▸ STAGE 1 — Proxy 손으로 만들기 (필수)

**목표**: Spring AOP 가 자동으로 해주는 일을 손으로 짜본다. JDK Dynamic Proxy + CGLIB 둘 다.

##### 1-1. JDK Dynamic Proxy 직접 작성

```java
public interface Greeter {
    String greet(String name);
}

public class GreeterImpl implements Greeter {
    public String greet(String name) { return "hello " + name; }
}

public class Stage1JdkProxy {
    public static void main(String[] args) {
        Greeter real = new GreeterImpl();

        Greeter proxy = (Greeter) Proxy.newProxyInstance(
            Greeter.class.getClassLoader(),
            new Class[]{Greeter.class},
            (p, method, methodArgs) -> {
                System.out.println("[before] " + method.getName());
                Object result = method.invoke(real, methodArgs);
                System.out.println("[after] " + method.getName() + " = " + result);
                return result;
            }
        );

        System.out.println("real.getClass()  = " + real.getClass().getName());
        System.out.println("proxy.getClass() = " + proxy.getClass().getName());
        proxy.greet("world");
    }
}
```

**관찰 포인트**:
- `proxy.getClass()` 가 `GreeterImpl` 이 아닌 `$Proxy0` / `$Proxy1` ... 형태
- `Greeter` 인터페이스를 구현하지만 실제 코드는 `InvocationHandler` 가 처리
- 인터페이스 없는 클래스에 적용하려면? → `ClassCastException` 발생

##### 1-2. CGLIB Proxy 직접 작성

```java
// import 는 Spring 내장 — net.sf.cglib 직접 추가하면 충돌
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;

public class Counter {                  // 인터페이스 없음
    public int next() { return 42; }
}

public class Stage1CglibProxy {
    public static void main(String[] args) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(Counter.class);
        enhancer.setCallback((MethodInterceptor) (obj, method, methodArgs, methodProxy) -> {
            System.out.println("[before] " + method.getName());
            Object result = methodProxy.invokeSuper(obj, methodArgs);
            System.out.println("[after] " + method.getName() + " = " + result);
            return result;
        });

        Counter proxy = (Counter) enhancer.create();
        System.out.println("proxy.getClass() = " + proxy.getClass().getName());
        proxy.next();
    }
}
```

**관찰 포인트**:
- `proxy.getClass()` 가 `Counter$$EnhancerByCGLIB$$...` 형태 (Counter 의 자식)
- `Counter extends Object` 인데 프록시는 `Counter` 를 상속 → `instanceof Counter` 가 true
- `final class Counter` 로 바꾸면? → `IllegalArgumentException` (final 클래스 상속 불가)

##### 1-3. Spring AOP 의 프록시 확인

```java
@SpringBootApplication
public class Stage1SpringProxy {
    public static void main(String[] args) {
        var ctx = SpringApplication.run(Stage1SpringProxy.class, args);

        TransferService svc = ctx.getBean(TransferService.class);
        System.out.println("getBean class = " + svc.getClass().getName());
        // 출력: TransferService$$EnhancerBySpringCGLIB$$...
        // → 진짜 TransferService 가 아닌 프록시
    }
}

@Service
public class TransferService {
    @Transactional
    public void transfer(long from, long to, BigDecimal amount) { /* ... */ }
}
```

**관찰 포인트**:
- 컨테이너가 반환하는 객체가 진짜가 아님
- `@Transactional` 이 붙은 메서드가 있어서 Spring 이 자동으로 프록시 생성
- `@Transactional` 모두 제거하면? → 진짜 객체 반환 (프록시 X)

##### 1-4. JDK vs CGLIB — Spring 의 선택 규칙

```java
public interface TransferService {
    void transfer(long from, long to, BigDecimal amount);
}

@Service
public class TransferServiceImpl implements TransferService {
    @Transactional
    public void transfer(long from, long to, BigDecimal amount) { /* ... */ }
}
```

**관찰 포인트**:
- 인터페이스 있는 경우 — `spring.aop.proxy-target-class=false` 면 JDK Proxy, `true` (Spring Boot 2.0+ 기본) 면 CGLIB
- 인터페이스 없는 경우 — 무조건 CGLIB
- `getClass().getName()` 으로 어느 쪽인지 확인

##### 1-5. STAGE 1 결과 정리

`measurements.md` 또는 별도 섹션에:
```
## STAGE 1 — Proxy 손으로 만들기 (직접 관찰)

JDK Dynamic Proxy 클래스명: $Proxy0 / $Proxy1 ...
CGLIB Proxy 클래스명: Counter$$EnhancerByCGLIB$$...
Spring ctx.getBean(X) 클래스명: X$$EnhancerBySpringCGLIB$$... (CGLIB 기본)
final 클래스에 CGLIB 적용 결과: IllegalArgumentException
인터페이스 없는 클래스에 JDK Proxy 적용 결과: ClassCastException
@Transactional 없는 Bean 의 getBean().getClass(): 진짜 클래스 (프록시 X)
```


### [목 11:00 — Ready PR 전환 + 코드 발표] — STAGE 2 ~ STAGE 4

> STAGE 1 (손 관찰) 은 화요일까지. 목요일까지는 `@Transactional` 분해 (2-1) → `@Aspect` 자작 (2-2~2-4) → STAGE 3 측정 → STAGE 4 self-invocation 함정.

#### ▸ STAGE 2 — Spring AOP 로 본인 도메인 적용 (필수)

##### 2-1. `@Transactional` 분해 — **순진한 버전 → 함정 → ThreadLocal 해결** (모두 공통, **목요일까지**)

**🔴 핵심 학습 단계** — 이 흐름이 5 주차 가장 중요한 학습 포인트. 한 번에 정답 짜지 말고 순진한 버전 → 함정 발견 → 실제 해결 순서로.

**Step 1 — 순진한 버전 (틀린 코드, 일부러 짜본다)**

```java
@Target(METHOD)
@Retention(RUNTIME)
public @interface MyTransactional {}

@Aspect
@Component
public class NaiveTransactionalAspect {
    private final DataSource dataSource;
    public NaiveTransactionalAspect(DataSource dataSource) { this.dataSource = dataSource; }

    @Around("@annotation(MyTransactional)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        try (Connection conn = dataSource.getConnection()) {     // ← 새 conn 1
            conn.setAutoCommit(false);
            System.out.println("[TX] begin");
            try {
                Object result = pjp.proceed();
                conn.commit();
                System.out.println("[TX] commit");
                return result;
            } catch (Throwable t) {
                conn.rollback();
                throw t;
            }
        }
    }
}

@Service
public class TransferService {
    private final TransferRepository repo;
    public TransferService(TransferRepository repo) { this.repo = repo; }

    @MyTransactional
    public void transfer(long from, long to, BigDecimal amount) {
        repo.minus(from, amount);     // ← Repository 가 conn 2 를 새로 꺼냄
        repo.plus(to, amount);        // ← conn 3 또 새로
    }
}

@Repository
public class TransferRepository {
    private final DataSource dataSource;
    public TransferRepository(DataSource dataSource) { this.dataSource = dataSource; }

    public void minus(long id, BigDecimal amount) {
        try (Connection conn = dataSource.getConnection()) {     // ← 새 conn
            // ... UPDATE
        }
    }
}
```

**Step 2 — 일부러 깨뜨려서 함정 확인**

`transfer()` 도중 `repo.plus()` 에서 예외 발생시키고 DB 확인:

```java
@MyTransactional
public void transfer(long from, long to, BigDecimal amount) {
    repo.minus(from, amount);
    if (true) throw new RuntimeException("일부러 실패");
    repo.plus(to, amount);
}
```

| 기대 | 실제 |
|---|---|
| from 차감 롤백되어야 함 | **from 차감 그대로 남음** (커밋되어버림) |
| Aspect 의 `conn.rollback()` 으로 복구 | Aspect 의 conn 과 Repository 의 conn 이 **다른 트랜잭션** — rollback 효과 없음 |

→ Aspect 의 `[TX] begin` 은 Aspect 의 conn 에 대해서만 유효. Repository 의 conn 은 autoCommit=true (HikariCP 기본) 라서 매 UPDATE 가 즉시 commit. **트랜잭션 묶음이 아님.**

**Step 3 — ThreadLocal 로 같은 Connection 공유 (실제 해결)**

```java
@Aspect
@Component
public class MyTransactionalAspect {
    private final DataSource dataSource;
    // ★ 현재 스레드에 묶인 Connection 보관
    private static final ThreadLocal<Connection> TX_CONN = new ThreadLocal<>();

    public MyTransactionalAspect(DataSource dataSource) { this.dataSource = dataSource; }

    @Around("@annotation(MyTransactional)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Connection conn = dataSource.getConnection();
        conn.setAutoCommit(false);
        TX_CONN.set(conn);                                       // ★ 스레드에 바인딩
        System.out.println("[TX] begin — " + pjp.getSignature().getName());
        try {
            Object result = pjp.proceed();
            conn.commit();
            System.out.println("[TX] commit");
            return result;
        } catch (Throwable t) {
            conn.rollback();
            System.out.println("[TX] rollback — " + t.getMessage());
            throw t;
        } finally {
            TX_CONN.remove();                                    // ★ 누수 방지
            conn.setAutoCommit(true);
            conn.close();
        }
    }

    // ★ Repository 가 이 메서드로 Connection 받아야 같은 트랜잭션
    public static Connection currentConnection(DataSource ds) throws SQLException {
        Connection conn = TX_CONN.get();
        return (conn != null) ? conn : ds.getConnection();       // 트랜잭션 밖이면 새 conn
    }
}

@Repository
public class TransferRepository {
    private final DataSource dataSource;
    public TransferRepository(DataSource dataSource) { this.dataSource = dataSource; }

    public void minus(long id, BigDecimal amount) throws SQLException {
        Connection conn = MyTransactionalAspect.currentConnection(dataSource);
        // ... UPDATE — Aspect 가 보관한 conn 과 동일 → 같은 트랜잭션
        // 단 트랜잭션 밖에서 호출하면 새 conn (autoCommit=true)
    }
}
```

**관찰 포인트**:
- Step 2 의 함정 재현 후 Step 3 으로 수정하면 from 차감도 같이 롤백됨
- Repository 가 `currentConnection()` 호출하는 게 어색 → 실제 Spring 은 `DataSourceUtils.getConnection(dataSource)` 한 줄로 추상화
- `TX_CONN.remove()` 안 하면 스레드풀에서 다음 요청이 이전 conn 받음 → 메모리 누수 + 트랜잭션 오염

> 핵심: 이게 Spring 의 `TransactionSynchronizationManager` 의 본질. ThreadLocal 로 현재 트랜잭션 컨텍스트 보관 → 같은 스레드 안에서는 어디서든 같은 Connection. 3 주차 `conn.setAutoCommit(false)` 를 직접 다뤘던 흐름이 5 주차에 ThreadLocal 추상화로 정리됨.
>
> 실무 `@Transactional` 은 여기에 `PlatformTransactionManager` 추상화 + propagation (REQUIRED / REQUIRES_NEW / NESTED) + isolation 까지 다루지만, 본질은 위 ThreadLocal + Around advice.

##### 2-2. AOP 체이닝 + `@Order` — 양파 껍질 관찰

`@MyTransactional` + `@Audited` + `@Timed` 를 한 메서드에 모두 붙이면 advice 호출 순서가 어떻게 되는가:

```java
@Service
public class OrderService {
    @MyTransactional
    @Audited(action = "PLACE_ORDER")
    @Timed
    public Long placeOrder(long userId, List<Long> items) { /* ... */ }
}
```

**Spring 의 기본 순서** (명시 없을 때):
- `@Order` 없으면 advice 적용 순서가 불확정 — 로깅이 트랜잭션 안에 들어갈 수도, 밖에 있을 수도 있음
- 트랜잭션 advice 가 바깥이어야 안전 — 안에 있으면 트랜잭션 commit 전에 로그 / 감사 기록이 먼저 들어가 불일치 발생 가능

**`@Order` 로 양파 껍질 순서 명시**:

```java
@Aspect @Component @Order(1)   // ← 가장 바깥
public class MyTransactionalAspect { /* ... */ }

@Aspect @Component @Order(2)
public class AuditAspect { /* ... */ }

@Aspect @Component @Order(3)   // ← 가장 안쪽
public class TimingAspect { /* ... */ }
```

**호출 순서** (정상 종료):
```
[TX] begin
  [Audit] before
    [Timed] start
      실제 메서드
    [Timed] end (Xms)
  [Audit] success
[TX] commit
```

**관찰 포인트**:
- 트랜잭션이 가장 바깥 → 감사 / 측정 advice 가 commit 시점을 못 봄 → "감사 로그는 트랜잭션 안인가 밖인가" 설계 결정 필요
- 만약 감사 로그를 commit 후로 옮기고 싶다면 → `@TransactionalEventListener(AFTER_COMMIT)` (6 주차)
- `@Order` 숫자 작은 게 바깥. 헷갈리기 쉬움 (Spring `Ordered.HIGHEST_PRECEDENCE = Integer.MIN_VALUE` 라는 컨벤션)

##### 2-3. `@Aspect` + Pointcut 표현식 3 가지

```java
@Aspect
@Component
public class PointcutDemoAspect {

    // (1) execution — 패키지 + 메서드 패턴
    @Before("execution(* com.example.service..*Service.*(..))")
    public void logServiceCall(JoinPoint jp) {
        System.out.println("[exec] " + jp.getSignature());
    }

    // (2) @annotation — FQN(완전 경로) 형식. 어노테이션 "타입" 으로 매칭만
    @Before("@annotation(com.example.audit.Audited)")
    public void logAudited(JoinPoint jp) {
        System.out.println("[audit] " + jp.getSignature());
    }

    // (3) within — 특정 클래스 내 모든 메서드
    @Before("within(com.example.service.OrderService)")
    public void logOrderService(JoinPoint jp) {
        System.out.println("[order] " + jp.getSignature());
    }
}
```

> **`@annotation` 표기 두 가지** (헷갈리기 쉬움):
> - **FQN (타입 참조)** — `@annotation(com.example.audit.Audited)`. 매칭만 함. 어노테이션 객체는 메서드로 안 받음
> - **파라미터 바인딩** — `@annotation(audited)` + 메서드 시그니처에 `Audited audited` 파라미터. 어노테이션 객체 자체를 받음 → `audited.action()` 등 속성 접근 가능
> - 소문자 `audited` 는 메서드 파라미터명. 어노테이션 타입이 import 되어 이름이 resolve 되면 단축명 (`@annotation(Audited)`) 도 동작하지만, 처음 배울 때는 FQN 권장 (이름 충돌 / 동명 클래스 시 명확)

**관찰 포인트**:
- 같은 메서드가 3 개 Pointcut 에 다 매칭되면 3 번 advice 호출
- `execution` 표현식의 `..` 은 임의 패키지 / 임의 인자 (구분 주의)
- `@annotation` 은 본인이 만든 어노테이션 직접 사용 가능

##### 2-4. Advice 5 종 직접 적용

```java
@Aspect
@Component
public class AllAdviceDemo {

    @Before("@annotation(Audited)")
    public void beforeAdvice(JoinPoint jp) {
        System.out.println("[1 Before] " + jp.getSignature());
    }

    @After("@annotation(Audited)")
    public void afterAdvice(JoinPoint jp) {
        System.out.println("[5 After] " + jp.getSignature() + " — finally");
    }

    @AfterReturning(pointcut = "@annotation(Audited)", returning = "result")
    public void afterReturning(JoinPoint jp, Object result) {
        System.out.println("[3 AfterReturning] " + result);
    }

    @AfterThrowing(pointcut = "@annotation(Audited)", throwing = "ex")
    public void afterThrowing(JoinPoint jp, Throwable ex) {
        System.out.println("[4 AfterThrowing] " + ex.getMessage());
    }

    @Around("@annotation(Audited)")
    public Object aroundAdvice(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("[2 Around 시작]");
        try {
            Object result = pjp.proceed();
            System.out.println("[2 Around 정상 종료]");
            return result;
        } catch (Throwable t) {
            System.out.println("[2 Around 예외]");
            throw t;
        }
    }
}
```

**호출 순서** (Spring Framework ≥ 5.2.7 / Spring Boot 2.3.1 + / 3.x, 정상 종료):
```
[2 Around 시작] → [1 Before] → 실제 메서드 → [3 AfterReturning] → [5 After] → [2 Around 정상 종료]
```

**호출 순서** (Spring Framework ≥ 5.2.7 / Spring Boot 2.3.1 + / 3.x, 예외 발생):
```
[2 Around 시작] → [1 Before] → 실제 메서드 (예외) → [4 AfterThrowing] → [5 After] → [2 Around 예외 처리]
```

> ⚠️ **Spring 5.2.6 vs 5.2.7 동작 변경** (Spring Issue #25186)
>
> 5.2.7 이전 — 같은 `@Aspect` 안의 advice 호출 순서가 메서드 선언 순서에 의존. Java 7+ 부터 메서드 선언 순서 보장이 사라지면서 비결정적이 됨.
>
> 5.2.7 이후 — 우선순위가 명시적으로 고정: **`Around > Before > After > AfterReturning > AfterThrowing`**. AspectJ 표준 시맨틱:
> - **"on the way in"** (들어갈 때) — 우선순위 높은 게 먼저 → `Around` 시작 → `Before`
> - **"on the way out"** (나갈 때) — 우선순위 높은 게 **가장 늦게** → `AfterReturning` → `After` → `Around` 종료
>
> → `@Around` 가 항상 양파의 가장 바깥. `proceed()` 호출 직후가 아니라 `AfterReturning` / `After` 가 모두 끝난 뒤 `Around` 종료 코드가 실행된다.
>
> 스터디원에게 "출력 찍어서 위 순서와 비교" 를 과제로. `measurements.md` 에 `[Around 종료]` 가 `[After]` 앞인지 뒤인지 한 줄 기록.

##### 2-5. 본인 도메인 어노테이션 자작

도메인 별 STEP 2 의 어노테이션 + Aspect 직접 짜기:

```java
// 예: 감사 로그 도메인
@Target(METHOD)
@Retention(RUNTIME)
public @interface Audited {
    String action() default "";
}

@Aspect
@Component
public class AuditAspect {
    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        long start = System.nanoTime();
        String userId = SecurityContext.getCurrentUserId();   // 가짜 컨텍스트
        try {
            Object result = pjp.proceed();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            System.out.println("[AUDIT] user=" + userId
                + " action=" + audited.action()
                + " method=" + pjp.getSignature().getName()
                + " args=" + Arrays.toString(pjp.getArgs())
                + " result=SUCCESS elapsed=" + elapsedMs + "ms");
            return result;
        } catch (Throwable t) {
            System.out.println("[AUDIT] user=" + userId
                + " action=" + audited.action()
                + " result=FAIL exception=" + t.getClass().getSimpleName());
            throw t;
        }
    }
}

@Service
public class OrderService {
    @Audited(action = "PLACE_ORDER")
    public Long placeOrder(long userId, List<Long> items) {
        // ... 주문 로직
        return newOrderId;
    }
}
```


#### ▸ STAGE 3 — 정량 측정 (필수)

##### 3-1. AOP 적용 전 / 후 응답 시간

> ⚠️ **JVM 웜업 주의**: 측정 전에 같은 메서드 5,000 회 호출로 JIT 컴파일. 4 주차 STAGE 3 의 측정 원칙 그대로.

```java
// AOP 없음 — 순수 메서드 호출
long t1 = System.nanoTime();
for (int i = 0; i < 1_000_000; i++) {
    service.simpleMethod();
}
System.out.println("순수: " + (System.nanoTime() - t1) / 1_000_000 + "ms");

// AOP 적용 — @Timed 어노테이션 있음
long t2 = System.nanoTime();
for (int i = 0; i < 1_000_000; i++) {
    timedService.simpleMethod();
}
System.out.println("AOP: " + (System.nanoTime() - t2) / 1_000_000 + "ms");

// 오버헤드 = (t2 - t1) / 1M / nanoTime 환산
```

##### 3-2. JDK Dynamic Proxy vs CGLIB 속도 비교

같은 메서드를 두 프록시 방식으로 1M 회 호출:

| 방식 | 1M 회 호출 시간 |
|---|---|
| 순수 메서드 호출 (프록시 X) | (측정) |
| JDK Dynamic Proxy | (측정) |
| CGLIB Proxy | (측정) |

**관찰 포인트** (Java 21 기준):
- JDK Proxy 와 CGLIB 의 **런타임 호출 비용은 JIT 웜업 후 사실상 동등** — Java 8 시절의 "리플렉션이 느리다" 통념이 더 이상 통하지 않음
- 오히려 CGLIB 는 프록시 클래스 바이트코드 생성 비용 → **부팅 시 비용**이 JDK Proxy 보다 큼 (런타임 호출 비용은 동등)
- Spring Boot 2.0+ 가 CGLIB 를 기본으로 바꾼 이유는 **성능이 아님** — "인터페이스 없는 Bean 도 일관 처리 + 생성자 주입 시 타입 안전성" 이 이유
- → 정확한 메커니즘 (코어 리플렉션의 MethodHandle 재구현 = Java 18 / C2 JIT 인라이닝 = 상시 동작) 보다 **본인 측정값**을 신뢰. 예단 금지. 5 회 평균 + JIT 웜업 후

##### 3-3. `getClass()` 출력 매트릭스

| 케이스 | `ctx.getBean(X.class).getClass()` |
|---|---|
| 인터페이스 없음 + `@Transactional` 없음 | 진짜 클래스 |
| 인터페이스 없음 + `@Transactional` 있음 | `X$$EnhancerBySpringCGLIB$$...` |
| 인터페이스 있음 + `@Transactional` 없음 | 진짜 클래스 |
| 인터페이스 있음 + `@Transactional` 있음 (Spring Boot 2.0+) | `X$$EnhancerBySpringCGLIB$$...` |
| 인터페이스 있음 + `proxy-target-class=false` 강제 | `$Proxy0` (JDK) |

##### 3-4. BeanPostProcessor 추가 1 개 확인 (4 주차 회수)

```java
// 4 주차에서 본 internal* 5 개 + 5 주차에 추가되는 1 개
@SpringBootApplication
public class Stage3BeanPostProcessors {
    public static void main(String[] args) {
        var ctx = SpringApplication.run(Stage3BeanPostProcessors.class, args);
        for (String name : ctx.getBeanDefinitionNames()) {
            if (name.contains("internal") || name.contains("AutoProxy")) {
                System.out.println(" - " + name);
            }
        }
    }
}
```

**관찰 포인트**:
- 4 주차 `internal*` 5 개 그대로
- `org.springframework.aop.config.internalAutoProxyCreator` 추가
- 또는 `org.springframework.context.annotation.internalAutoProxyCreator`
- 이게 모든 `@Aspect` 를 찾아서 프록시 생성하는 주체

##### 3-5. 측정 표 (5 회 평균)

| 항목 | 측정값 |
|---|---|
| 순수 메서드 호출 1M 회 | (ms) |
| JDK Proxy 호출 1M 회 | (ms) |
| CGLIB Proxy 호출 1M 회 | (ms) |
| AOP 적용 전 응답 시간 | (ms) |
| AOP 적용 후 응답 시간 | (ms) |
| AOP 오버헤드 % | (%) |
| 4 주차 `internal*` Bean 수 | 5 (변동 없음) |
| 5 주차 추가 `AutoProxy` Bean 수 | 1 |


#### ▸ STAGE 4 — self-invocation 함정 + CGLIB 한계 (필수, 면접 직결)

##### 4-1. self-invocation 재현

```java
@Service
public class OrderService {
    @MyTransactional
    public void outerMethod(Long orderId) {
        // 외부에서 호출되면 프록시가 가로채서 [TX] begin 출력
        innerMethod(orderId);   // ← this.innerMethod() — 프록시 우회
    }

    @MyTransactional
    public void innerMethod(Long orderId) {
        // outerMethod 에서 호출 시 [TX] begin 출력 안 됨 — 프록시 안 거침
    }
}

public class Stage4SelfInvocation {
    public static void main(String[] args) {
        var ctx = SpringApplication.run(...);
        OrderService svc = ctx.getBean(OrderService.class);

        System.out.println("=== outerMethod 호출 ===");
        svc.outerMethod(1L);
        // 출력:
        //   [TX] begin — outerMethod   ← 외부 호출은 프록시 거침
        //   (innerMethod 의 [TX] begin 출력 안 됨)   ← self-invocation
        //   [TX] commit
    }
}
```

**왜 안 먹는가**:
- `ctx.getBean(OrderService.class)` 가 반환하는 건 프록시
- 프록시의 `outerMethod()` 가 호출되면 `MyTransactionalAspect` 가 가로챔 → `[TX] begin`
- 가로챈 후 실제 객체의 `outerMethod()` 호출 → 그 안에서 `this.innerMethod()` 는 **실제 객체의** `innerMethod()` 직접 호출 (프록시 거치지 않음)
- → `innerMethod` 의 `@MyTransactional` 무시됨

##### 4-2. 해결 3 가지 직접 적용

| 해결책 | 코드 | 트레이드오프 |
|---|---|---|
| **(a) 자기 자신 주입** | `@Autowired @Lazy private OrderService self;` 후 `self.innerMethod()` | 동작은 함. 그러나 자기 자신을 주입하는 게 이상 — 설계 냄새 |
| **(b) ApplicationContext** | `ctx.getBean(OrderService.class).innerMethod()` | Service Locator 패턴 — 안티패턴 |
| **(c) 클래스 분리** | `innerMethod` 를 별도 `InnerOrderService` 로 분리 후 주입 | 가장 권장 — 근본 해결 |

```java
// 해결 (a) — 자기 자신 주입
@Service
public class OrderService {
    @Autowired @Lazy
    private OrderService self;

    @MyTransactional
    public void outerMethod(Long orderId) {
        self.innerMethod(orderId);   // 프록시 거침 → [TX] begin 출력됨
    }

    @MyTransactional
    public void innerMethod(Long orderId) { /* ... */ }
}
```

```java
// 해결 (c) — 클래스 분리
@Service
public class OrderService {
    private final InnerOrderService inner;
    public OrderService(InnerOrderService inner) { this.inner = inner; }

    @MyTransactional
    public void outerMethod(Long orderId) {
        inner.innerMethod(orderId);   // 다른 객체의 프록시 → [TX] begin 출력됨
    }
}

@Service
public class InnerOrderService {
    @MyTransactional
    public void innerMethod(Long orderId) { /* ... */ }
}
```

##### 4-3. CGLIB 한계 — final / private / static

```java
@Service
public final class CannotProxy {           // ❌ final 클래스 — CGLIB 못 상속
    @Transactional
    public void method() {}
}
// 부팅 실패: Cannot subclass final class

@Service
public class CannotProxyMethod {
    @Transactional
    public final void method() {}          // ❌ final 메서드 — 오버라이드 불가
    // 부팅은 성공. Spring 6 + CGLIB 가 WARN 로그 출력 후 advice 스킵

    @Transactional
    private void privateMethod() {}        // ❌ private — 외부 호출 자체 불가, 프록시 의미 없음

    @Transactional
    public static void staticMethod() {}   // ❌ static — 객체 메서드 아님
}
```

**관찰 포인트**:
- final 클래스 — 부팅 실패 (`Cannot subclass final class`)
- final 메서드 — 부팅은 성공. Spring 이 WARN 로그 출력 후 advice 스킵 (`Unable to proxy method` 류)
- private — `@Transactional` 무시
- static — `@Transactional` 무시
- 모두 면접에서 자주 나오는 "왜 안 먹는가" 케이스

##### 4-4. 측정 항목

| 항목 | 결과 |
|---|---|
| self-invocation 시 `[TX] begin` 출력 여부 | 출력 안 됨 |
| 해결 (a) 자기 자신 주입 후 출력 여부 | 출력됨 |
| 해결 (c) 클래스 분리 후 출력 여부 | 출력됨 |
| final 클래스 + `@Transactional` 부팅 결과 | 실패 |
| final 메서드 + `@Transactional` advice 적용 여부 | WARN 로그 출력 후 스킵 |


━━━━━━━━━━━━━━━━━━━━━━━━━━
─── 여기 아래는 선택 (시간 여유 시) ───
━━━━━━━━━━━━━━━━━━━━━━━━━━

### [선택] ▸ STAGE 5 — `@EventListener` 6 주차 브릿지

> ⏰ **언제 하나**: Ready PR (목 11:00) 이후 **여유 시에만**. STAGE 1 ~ 4 가 우선. 늦어도 **6 주차 시작 전 (다음 목)** 까지 안 해도 됨.

##### 5-1. AOP 와 `@EventListener` 의 결 비슷한 점

```java
// AOP — 암묵적 가로채기 (메서드 호출 시 자동 advice 실행)
@Aspect
@Component
public class OrderAuditAspect {
    @AfterReturning("@annotation(Audited)")
    public void onSuccess(JoinPoint jp) {
        System.out.println("[감사] " + jp.getSignature());
    }
}

// EventListener — 명시적 이벤트 발행 (publisher 가 직접 호출)
@Component
public class OrderEventListener {
    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        System.out.println("[리스너] 주문 발생 — " + event.orderId());
    }
}

@Service
public class OrderService {
    private final ApplicationEventPublisher publisher;
    public OrderService(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }
    public Long placeOrder(...) {
        // ... 주문 로직
        publisher.publishEvent(new OrderPlacedEvent(newOrderId));
        return newOrderId;
    }
}
```

##### 5-2. AOP vs `@EventListener` 차이

| 축 | AOP | `@EventListener` |
|---|---|---|
| 트리거 방식 | 암묵적 (메서드 호출 시 자동) | 명시적 (`publisher.publishEvent`) |
| 결합도 | 낮음 (Aspect 가 대상 모름) | 낮음 (Publisher 와 Listener 분리) |
| 가시성 | 코드에 안 보임 (어노테이션 + Aspect 분리) | `publishEvent` 호출이 코드에 명시됨 |
| 동기 / 비동기 | 동기 (기본) | 동기 (기본) / `@Async` 로 비동기 |
| 트랜잭션 연동 | 같은 트랜잭션 안 | `@TransactionalEventListener` 로 commit 후 처리 가능 |
| 6 주차 본론 | (5 주차) | (6 주차) |

##### 5-3. `@TransactionalEventListener` — AOP + 이벤트 결합

```java
@Component
public class OrderEventListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(OrderPlacedEvent event) {
        // 트랜잭션 commit 후에만 실행 — 결제 / 알림 / 외부 API 호출 안전
    }
}
```

- AOP 의 `@Transactional` 과 `@EventListener` 가 만나는 지점
- 6 주차 (event) 의 핵심 패턴


━━━━━━━━━━━━━━━━━━━━━━━━━━
금지 키워드 — STAGE 1 ~ 2 동안만 (검색 금지)
━━━━━━━━━━━━━━━━━━━━━━━━━━
- Spring Security 의 `@PreAuthorize` 내부 동작 (11 주차 보호)
- AspectJ Load Time Weaving (LTW) — Spring AOP 의 런타임 위빙과 다름. 학습 범위 밖
- ByteBuddy / Javassist — 바이트코드 조작 라이브러리. CGLIB 와 결 비슷하지만 범위 밖
- Spring `AopContext.currentProxy()` — self-invocation 의 임시 해결. (a) 자기 주입 / (c) 클래스 분리 먼저 익힌 후
- ProxyFactoryBean / `<aop:config>` — XML 기반 옛 방식
- Spring Cloud Sleuth / Micrometer (12 주차 관측)


━━━━━━━━━━━━━━━━━━━━━━━━━━
학습 기록 (자유)
━━━━━━━━━━━━━━━━━━━━━━━━━━

### 4 주차 회상 — 5 주차로 이어지는 지점

| 4 주차에서 본 것 | 5 주차에서 확장 |
|---|---|
| `@Configuration(proxyBeanMethods=true)` 가 CGLIB 프록시로 `@Bean` 메서드 가로챔 | `@Transactional` / `@Aspect` 가 같은 CGLIB 메커니즘으로 일반 메서드 가로챔 |
| `internal*` BeanPostProcessor 5 개 (`@Autowired`, `@PostConstruct` 처리) | `AspectJAutoProxyCreator` 1 개 추가 (`@Aspect` 처리) — 동일 메커니즘 |
| 컨테이너가 객체 **생성 / 주입** 책임 가짐 | 컨테이너가 객체 **메서드 호출** 까지 책임 가짐 |
| `ctx.getBean(X.class)` → 진짜 X (4 주차 대부분 케이스) | `ctx.getBean(X.class)` → X$$EnhancerBySpringCGLIB$$... (5 주차) |

### 5 주차 참고 질문 (답하고 싶은 만큼만)
- JDK Dynamic Proxy 와 CGLIB 의 본질적 차이 — 인터페이스 유무가 왜 결정적인가
- `ctx.getBean(X.class).getClass()` 가 진짜 X 가 아닌 이유
- `@Transactional` 이 한 줄로 begin / commit 을 자동으로 끼우는 메커니즘
- self-invocation 이 왜 작동 안 하는가 — `this` 와 프록시의 관계
- Advice 5 종 중 `@Around` 가 가장 일반적인 이유 — 나머지 4 종이 `@Around` 의 특수 케이스인지
- Pointcut 표현식 본인 예 1 개
- 4 주차의 `internal*` 5 개와 5 주차의 `AspectJAutoProxyCreator` 가 같은 메커니즘인 이유
- final / private / static 메서드에 `@Transactional` 이 안 먹는 이유
- AOP 와 `@EventListener` 의 결 비슷한 점 / 다른 점 (6 주차 예고)

### 면접 단골 + 본인 답
- **"`@Transactional` 이 안 먹는 경우 3 가지"** (self-invocation / private / final 메서드)
- **"JDK Dynamic Proxy vs CGLIB"** (인터페이스 유무 / 상속 vs 구현 / final 한계)
- **"AOP 의 5 가지 advice 와 호출 순서"**
- **"Pointcut 표현식 본인 예 1 개"** (`execution` / `@annotation` / `within`)
- **"AOP 가 작동하는 시점"** (`BeanPostProcessor.postProcessAfterInitialization`)
- **"`@Transactional` 분해해서 설명"** — `TransactionInterceptor` + `Around` advice + begin / commit / rollback
- **"Service 와 Repository 가 같은 트랜잭션을 공유하는 메커니즘"** — `TransactionSynchronizationManager` (ThreadLocal 기반). 본인이 직접 `TX_CONN.set/get` 짜본 경험 + 누수 방지 `remove()`
- **"여러 Aspect 가 같이 붙으면 트랜잭션이 안인가 밖인가"** — `@Order` 로 양파 껍질 순서 명시. 트랜잭션이 가장 바깥. commit 후 처리는 `@TransactionalEventListener(AFTER_COMMIT)`
- **"self-invocation 해결 방법 3 가지"** + 어느 것이 권장
- **"4 주차 IoC 컨테이너 + 5 주차 AOP 의 관계"** — 컨테이너가 객체 생성 책임 / AOP 가 메서드 호출 책임. BeanPostProcessor 가 다리

### 실무 확장 화두 (스터디 토론 / 면접 후속 질문)
- **`TransactionSynchronizationManager` 의 본질**: ThreadLocal 로 Connection / 트랜잭션 컨텍스트를 보관 — Aspect 에서 시작한 트랜잭션을 Repository 가 어떻게 같은 conn 으로 받는가. STAGE 2-1 Step 3 에서 직접 만든 `TX_CONN.set/get` 의 실무 추상화. 3 주차 `setAutoCommit(false)` 를 직접 다뤘던 경험이 여기서 정리됨
- **`@Order` 와 양파 껍질 순서**: 트랜잭션 advice 가 가장 바깥이어야 안전. 안쪽에 두면 commit 전에 감사 / 로그 / 알림이 먼저 발사되어 trans-data 불일치 가능. STAGE 2-2 에서 직접 관찰
- **AOP 적용 순서 (`@Order`)**: 여러 Aspect 가 같은 메서드에 매칭될 때 호출 순서 결정
- **`@Transactional` propagation / isolation**: REQUIRED / REQUIRES_NEW / NESTED 차이. 자작 `@MyTransactional` 에 직접 구현해보면 트랜잭션 컨텍스트 관리 (ThreadLocal) 의 어려움 체감
- **Spring AOP vs AspectJ**: Spring 은 런타임 위빙 (프록시), AspectJ 는 컴파일 / 로드 타임 위빙 (바이트코드 직접 수정). 적용 범위 / 성능 차이
- **`@Async` 의 self-invocation**: `@Transactional` 과 동일하게 self-invocation 시 비동기 X. 같은 프록시 메커니즘
- **`@Configuration(proxyBeanMethods=false)`** (4 주차 STAGE 5): 프록시 없으면 `@Bean` 메서드 호출이 매번 new — 싱글톤 보장 X. 5 주차의 프록시 원리와 정확히 같은 메커니즘
- **CGLIB 의 `final` 한계 대안**: 인터페이스 추출 후 JDK Proxy 또는 메서드 분리

### Proxy / AOP 선택 매트릭스 (면접 답변 기준)

| 상황 | 선택 | 이유 |
|---|---|---|
| 인터페이스 있는 Service | JDK Dynamic Proxy 또는 CGLIB | Spring Boot 2.0+ 기본은 CGLIB. JDK 강제는 `proxy-target-class=false` |
| 인터페이스 없는 Service / Controller | CGLIB | 클래스 상속만 가능. final 안 됨 주의 |
| 모든 `@Service` 메서드에 로깅 | `execution(* com.example.service..*Service.*(..))` | 패키지 + 클래스 패턴 |
| 특정 어노테이션 붙은 메서드만 | `@annotation(MyAnnotation)` | 의도 명확 + 어노테이션으로 표시 |
| `@Transactional` 이 안 먹음 | self-invocation / private / final 확인 | 클래스 분리 (c) 가 가장 권장 |
| 호출 전후 + 예외 모두 처리 | `@Around` | 가장 일반. 나머지 4 종은 특수 케이스 |


━━━━━━━━━━━━━━━━━━━━━━━━━━
막힐 때
━━━━━━━━━━━━━━━━━━━━━━━━━━
1. 직접 시도 (`CLAUDE.md` 룰)
2. AI 에 물어보기 — 3 단계 힌트만 받음 (코드 직답 X)
3. 디스코드 `#질문` 채널 — 코드 + 본인 Aspect + 적용 대상 Service 함께

**`@Transactional` (또는 자작 `@Aspect`) 이 안 먹는 경우 체크리스트**:
1. `ctx.getBean(X.class).getClass()` 로 프록시인지 확인 — 진짜 클래스면 AOP 적용 안 됨
2. self-invocation 아닌지 확인 — 같은 클래스 안에서 `this.method()` 호출하면 우회
3. 메서드가 `public` 인지 확인 — `private` / `protected` 는 CGLIB 한계 (Spring 6 이하)
4. 클래스 / 메서드가 `final` 아닌지 확인
5. Spring Boot 가 `@EnableAspectJAutoProxy` 활성화했는지 — `spring-boot-starter-aop` 의존성 추가 확인
6. `@Aspect` 클래스가 `@Component` 로 Bean 등록되어 있는지

**`UndeclaredThrowableException` 발생 시**: JDK Dynamic Proxy 에서 `InvocationHandler.invoke()` 가 checked exception 던질 때. `throws Throwable` 로 받아서 처리.

**`Cannot subclass final class` 부팅 실패**: CGLIB 가 final 클래스 상속 불가. 해당 클래스의 `final` 제거 또는 인터페이스 추출 후 JDK Proxy.

**advice 가 두 번 호출됨**: 같은 Pointcut 에 매칭되는 Aspect 가 여러 개. `@Order` 로 순서 명시 또는 Pointcut 좁히기.
