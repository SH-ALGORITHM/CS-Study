# 6주차 Spring Event — 용어 정리

> 5 주차의 Proxy / AOP 용어 정리와 같은 형식. STAGE 진행 전 또는 학습 중 막힐 때 참조.
>
> 시나리오 단어표 (13 개) 는 핵심만, 이 파일은 카테고리별 전체.

---

## 🌳 Event 본질 + publish-subscribe

| 용어 | 풀어쓰면 |
|---|---|
| **이벤트 (Event)** | "어떤 일이 일어났다" 라는 사실을 표현하는 객체. 과거형 이름 (`OrderPlacedEvent`) |
| **publish-subscribe 패턴** | 발행자 (publisher) / 구독자 (listener) 분리 패턴. publisher 는 listener 를 모름 |
| **publisher (발행자)** | 이벤트를 발행하는 쪽. `ApplicationEventPublisher.publishEvent(event)` |
| **listener (리스너)** | 이벤트를 받는 쪽. `@EventListener` 메서드 또는 `ApplicationListener` 인터페이스 구현 |
| **결합도 낮음** | publisher 는 listener 의 존재 / 개수 / 구현을 알 필요 없음 → 리스너 추가 / 제거가 publisher 코드에 영향 X |
| **과거형 이름** | `OrderPlaced` / `TransferCompleted` / `UserRegistered` — 이미 일어난 일. `PlaceOrder` 같은 명령형 금지 |
| **도메인 이벤트** | 비즈니스 도메인 사건. DDD 의 핵심 개념. 6 주차는 Spring 인프라 기술만 다룸 |

## 📤 ApplicationEventPublisher + @EventListener

| 용어 | 풀어쓰면 |
|---|---|
| **`ApplicationEventPublisher`** | 이벤트 발행 인터페이스. `publishEvent(event)` 한 줄 |
| **`ApplicationContext`** | `ApplicationEventPublisher` 를 상속함. 따로 주입 안 받고 `ApplicationContext` 로도 발행 가능 |
| **`@EventListener`** | 리스너 메서드 어노테이션. 기본 **동기** (publisher 와 같은 스레드) |
| **`ApplicationListener<E>`** | 리스너 인터페이스 (옛 방식). 한 클래스에 한 이벤트 |
| **`@EventListener` 메서드 시그니처** | `public void on(MyEvent e)` — `void` 또는 반환값 (다음 이벤트 발행 가능) |
| **`@EventListener(MyEvent.class)`** | 메서드에 파라미터 없이도 이벤트 받기 가능 |
| **`@EventListener({A.class, B.class})`** | 여러 이벤트 타입 받기 |
| **`@EventListener(condition = "...")`** | SpEL 조건으로 필터링. 예: `condition = "#event.amount > 1000"` |
| **payload-only 이벤트** | Spring 4.2+ 부터 `ApplicationEvent` 상속 의무 X. POJO / `record` 도 OK |
| **`PayloadApplicationEvent<T>`** | Spring 이 payload-only 이벤트를 내부적으로 감쌈. publisher 가 일반 객체 발행 시 |
| **`ApplicationEvent`** | Spring 의 옛 이벤트 base 클래스 (Spring 3 이하). 4.2+ 부터는 상속 불필요 |
| **`@EventListener` 메서드 등록 시점** | Bean 초기화 후. `EventListenerMethodProcessor` (4 주차 `internal*` 5 개 중 하나) 가 처리 |
| **`ApplicationListenerMethodAdapter`** | `@EventListener` 메서드를 `ApplicationListener` 로 감싸는 어댑터. 결국 두 방식이 같은 메커니즘 |
| **`SimpleApplicationEventMulticaster`** | 이벤트 → 리스너 분배 본체. 기본 동기. `setTaskExecutor(executor)` 호출 시 **모든 리스너 전역 비동기** — `@TransactionalEventListener` 의 phase 콜백 (ThreadLocal 기반) 과 충돌 위험. **per-listener `@Async` 가 정답** |
| **멀티캐스터 전역 비동기 vs `@Async`** | 전자 = 모든 리스너 / 거친 단위 / phase 충돌 / 비권장. 후자 = 개별 리스너 / 권장 |

## ⏱ @TransactionalEventListener + 4 phase

| 용어 | 풀어쓰면 |
|---|---|
| **`@TransactionalEventListener`** | 트랜잭션 상태 기반 리스너. 현재 트랜잭션에 콜백 등록 |
| **`TransactionPhase`** | 4 enum — `BEFORE_COMMIT` / `AFTER_COMMIT` / `AFTER_ROLLBACK` / `AFTER_COMPLETION` |
| **`BEFORE_COMMIT`** | commit 직전. 같은 트랜잭션 안. 마지막 검증 / 같은 DB 추가 INSERT |
| **`AFTER_COMMIT`** (기본) | commit 직후. 트랜잭션 종료. 외부 알림 / 이메일 / 외부 API |
| **`AFTER_ROLLBACK`** | rollback 직후. 보상 처리 (실패 통보, 캐시 정리, 외부 시스템 복구) |
| **`AFTER_COMPLETION`** | commit / rollback 무관. 트랜잭션 종료 후 항상 실행. 정리 / 로깅 |
| **`fallbackExecution`** | 기본 `false` — 트랜잭션 밖이면 무시. `true` 면 즉시 실행 (그냥 `@EventListener` 처럼) |
| **트랜잭션 밖 기본 동작** | 조용히 무시. WARN 로그조차 안 나옴 → 함정 |
| **`TransactionSynchronization`** | 트랜잭션 콜백 인터페이스. `@TransactionalEventListener` 가 내부적으로 사용 |
| **`TransactionSynchronizationManager`** | 5 주차의 ThreadLocal `TX_CONN` 의 실무 추상화. `@TransactionalEventListener` 가 콜백 등록 |
| **`registerSynchronization()`** | 현재 트랜잭션에 콜백 등록. phase 별로 적절한 메서드 (`beforeCommit / afterCommit / afterCompletion`) |
| **`BEFORE_COMMIT` 에서 예외** | 본 트랜잭션 rollback → `AFTER_ROLLBACK` 호출 |
| **`AFTER_COMMIT` 에서 예외 (동기)** | 이미 commit 됨 → rollback 불가. **예외는 commit 호출 쪽으로 전파**. `TransactionSynchronizationUtils` 가 잡지 않음 → publisher 메서드에서 throw 됨 |
| **`AFTER_COMMIT` 에서 예외 (`@Async`)** | 별 스레드라 publisher 에 전파 X. `AsyncUncaughtExceptionHandler` 가 잡음 |
| **`AFTER_COMPLETION` 에서 예외** | `TransactionSynchronizationUtils` 가 로그로 삼킴 (swallow) → 격리됨. 다른 리스너 / publisher 영향 X |
| **두 phase 예외 동작 차이** | AFTER_COMMIT = 전파 (동기) / AFTER_COMPLETION = 격리. 면접 디테일 |
| **새 트랜잭션 필요 시** | `@TransactionalEventListener` 메서드에 `@Transactional(propagation = REQUIRES_NEW)` 추가 — AFTER_COMMIT 후 새 트랜잭션 시작 |
| **AFTER_COMMIT 에서 DB 쓰기 no-op 함정** | 본 트랜잭션은 이미 commit / 정리. 리스너의 `jdbc.update()` 가 예외도 없이 무시될 수 있음. 해결 = `@Transactional(REQUIRES_NEW)` |
| **`@Async` + AFTER_COMMIT 새 스레드 함정** | 새 스레드라 `TransactionSynchronizationManager` / 영속성 컨텍스트 다 날아감. DB 조회 / JPA Lazy 로딩 폭발. 해결 = `REQUIRES_NEW` + payload 풍부하게 |

## ⚡ @Async + ThreadPoolTaskExecutor

| 용어 | 풀어쓰면 |
|---|---|
| **`@Async`** | 메서드 / 리스너를 별 스레드에서 실행. 프록시 메커니즘 |
| **`@EnableAsync`** | `@Async` 활성화 어노테이션. `@SpringBootApplication` 옆에 |
| **`ThreadPoolTaskExecutor`** | Spring 의 표준 비동기 executor. core / max / queue 설정 |
| **`SimpleAsyncTaskExecutor`** | Spring 의 옛 기본 executor (이름이 헷갈림). 플랫폼 스레드 모드 — **요청마다 새 플랫폼 스레드** → 위험. Boot 3.2+ 가상 스레드 모드 — 작업마다 새 가상 스레드 생성하지만 생성/폐기 비용이 거의 없어 풀링 불필요 (이게 가상 스레드의 본질) |
| **`applicationTaskExecutor`** | Spring Boot 2.1+ 자동 등록 빈 이름. 표준 |
| **Spring Boot 자동 기본값 (함정)** | core=8 / queue=**Integer.MAX_VALUE** / max=Integer.MAX_VALUE. **무한 큐 때문에 max 도달 불가** → 사실상 8 개 고정 + 무한 큐 |
| **`spring.task.execution.*`** | 자동 executor 설정 프로퍼티 — `pool.core-size` / `pool.max-size` / `pool.queue-capacity` / `thread-name-prefix` |
| **`corePoolSize`** | 기본 스레드 수. 평소 유지 |
| **`maxPoolSize`** | 최대 스레드 수. **큐가 가득 차야** 여기까지 늘림 |
| **`queueCapacity`** | 대기 큐 사이즈. core 다 차면 큐에 쌓임. 무한이면 max 도달 불가 |
| **ThreadPoolExecutor 동작 규칙** | (1) 들어온 작업 < core → 새 스레드 / (2) core 다 참 → 큐 / (3) **큐도 다 참 → max 까지** / (4) max 도 다 참 → `RejectedExecutionHandler` |
| **`RejectedExecutionHandler`** | 큐도 가득 + max 도 다 찼을 때 정책. `CallerRunsPolicy` (호출 스레드가 직접) / `AbortPolicy` (예외) / `DiscardPolicy` (조용히 버림) |
| **`@Async("executorName")`** | 여러 executor 중 특정 빈 이름 지정 |
| **`Future<T>` / `CompletableFuture<T>`** | `@Async` 메서드 반환 타입. 호출자가 결과 / 예외 받을 수 있음 |
| **`void` 반환 시 예외** | 호출자에 전파 X. **사라짐**. `AsyncUncaughtExceptionHandler` 등록 필요 |
| **`AsyncUncaughtExceptionHandler`** | void `@Async` 메서드의 예외 처리. `AsyncConfigurer` 구현 |
| **`AsyncConfigurer`** | `@Async` 의 executor + 예외 핸들러 동시 설정 인터페이스. `getAsyncExecutor()` 오버라이드 시 그게 우선 (Spring 기본 해석 무시) |

## 🧵 Virtual Thread (Java 21 + Spring Boot 3.2)

| 용어 | 풀어쓰면 |
|---|---|
| **Virtual Thread** | Java 21 정식. JVM 이 관리하는 경량 스레드. 블로킹 시 캐리어 스레드 점유 안 함 → 거의 무제한 생성 |
| **풀링 불필요** | 가상 스레드는 풀에 넣고 재사용하지 않음. **작업마다 새로 만들지만** 생성/폐기 비용이 거의 없어 부담 없음. "왜 풀을 안 쓰나" = 면접 후속 질문 |
| **Platform Thread** | OS 스레드 1:1 매핑. 비싸고 개수 제한 (보통 수천 개) |
| **`spring.threads.virtual.enabled=true`** | Boot 3.2+ 한 줄 설정. `applicationTaskExecutor` 가 가상 스레드 기반 `SimpleAsyncTaskExecutor` 로 자동 교체 |
| **I/O 바운드** | HTTP / DB / 외부 API 호출. 대부분 블로킹 대기 → 가상 스레드 최적. **풀 사이즈 튜닝 의미 없음** |
| **CPU 바운드** | 계산 집약. 가상 스레드 의미 없음 (캐리어 = ForkJoinPool, CPU 코어 수만큼) |
| **가상 스레드 + self-invocation** | **무관**. 프록시 메커니즘 이슈는 그대로 — 비동기 도구 바뀐다고 사라지지 않음 |
| **`Thread.ofVirtual()`** | 가상 스레드 직접 생성 (Spring 안 거치고). 학습용 |
| **`StructuredTaskScope`** | Java 21 preview / 25 정식. 가상 스레드 묶음 관리 — 6 주차 범위 밖 |

## 📬 신뢰성 / 메시지 큐 경계

| 용어 | 풀어쓰면 |
|---|---|
| **at-most-once** | 최대 1 회. 유실 가능. Spring Event + `AFTER_COMMIT` + `@Async` 가 이 수준 |
| **at-least-once** | 최소 1 회. 중복 가능 (멱등성 필요). Kafka / RabbitMQ 기본 |
| **exactly-once** | 정확히 1 회. 분산 환경에서 사실상 불가 / 어렵 |
| **Transactional Outbox** | 패턴. 본 트랜잭션과 같이 outbox 테이블에 INSERT → 별도 Worker 가 읽어서 MQ 발행. at-least-once 보장 |
| **Spring Event 의 신뢰성 천장** | 단일 JVM / 인메모리. commit 직후 ~ 리스너 실행 전 프로세스 죽으면 유실. 보장 필요하면 outbox + MQ |
| **멱등성** (Idempotency) | 같은 작업 N 번 해도 결과 동일. at-least-once 환경 필수 |

## ⚠️ self-invocation 함정 (5 주차 회수)

| 용어 | 풀어쓰면 |
|---|---|
| **self-invocation** | 같은 클래스 안 `this.method()` 호출 → 프록시 우회 → `@Async` 무시 |
| **5 주차와 같은 메커니즘** | `@Transactional` self-invocation 과 정확히 같음. `this` = 원본 객체 |
| **해결 (a) 자기 자신 주입** | `@Autowired @Lazy private Self self;` 후 `self.asyncMethod()` |
| **해결 (b) 클래스 분리** | asyncMethod 를 별도 Service 로 빼고 주입. 가장 권장 |
| **해결 (c) ApplicationEventPublisher** | `publishEvent` 로 발행 → 다른 리스너 클래스가 받음. **가장 6 주차스러운 우회** |
| **`@Async` 가 안 먹는 이유 본질** | 프록시가 가로채야 별 스레드 — `this` 는 원본 → 프록시 우회 |

## 🔄 ApplicationListener vs @EventListener

| 용어 | 풀어쓰면 |
|---|---|
| **`ApplicationListener<E>`** | 옛 방식. 인터페이스 구현. 한 클래스에 한 이벤트만 |
| **`@EventListener`** | 새 방식 (Spring 4.2+). 메서드 어노테이션. 한 클래스에 여러 이벤트 메서드 가능 |
| **`@EventListener` 가 권장되는 이유** | 한 클래스에 여러 이벤트 가능 + 메서드 별 `condition` / `@Order` 적용 + payload-only 자연 |
| **내부 동작 동일** | `@EventListener` 메서드는 `ApplicationListenerMethodAdapter` 로 감싸져 `ApplicationListener` 로 변환됨 |
| **`SmartApplicationListener`** | 동적 이벤트 타입 매칭. `supportsEventType` 메서드 직접 구현. 거의 안 씀 |
| **`ApplicationContextEvent`** | Spring 컨테이너 라이프사이클 이벤트 (`ContextRefreshedEvent`, `ContextClosedEvent` 등) |
| **`ContextRefreshedEvent`** | 컨테이너 초기화 완료 시점. `@EventListener(ContextRefreshedEvent.class)` 로 받기 |
| **`ApplicationReadyEvent`** | Spring Boot 의 어플리케이션 시작 완료 이벤트. `@EventListener` 대신 `@PostConstruct` 권장 (단순 초기화) |

## 🎯 AOP vs Event 결정 매트릭스

| 축 | AOP (5 주차) | Event (6 주차) |
|---|---|---|
| 트리거 | 암묵적 (어노테이션) | 명시적 (`publishEvent`) |
| 가시성 | 메서드 보기엔 안 보임 | publisher 코드에 한 줄 명시 |
| 적용 범위 | 같은 패키지 / 횡단 관심사 | 모듈 간 / 부수 효과 전파 |
| 트랜잭션 시점 | 양파 — 안 / 밖만 | 시간축 — 4 phase 자유 |
| 비동기 | `@Async` (같은 함정) | `@Async` (같은 함정) |
| 적합 — 로깅 / 측정 / 권한 / 캐싱 / 트랜잭션 | ✓ | |
| 적합 — 알림 / 외부 API / 통계 / 다른 모듈 | | ✓ |
| 회색지대 — 감사 로그 (외부 시스템) | | ✓ (AFTER_COMMIT) |
| 회색지대 — 감사 로그 (같은 DB) | ✓ | ✓ (BEFORE_COMMIT) |

## 🌉 5 주차 회수 — 양파 한계

| 용어 | 풀어쓰면 |
|---|---|
| **양파 구조** | `@Order` 로 advice 호출 순서 명시 — 안 / 밖 구조만 가능 |
| **TX 가 가장 바깥인 패턴** | 5 주차 권장 — Audit 가 TX 안쪽 → commit 전 발사 → 외부 시스템 회수 불가 |
| **양파의 한계** | 시간축 (commit 전 / 후) 분리 불가. 안쪽 advice 는 항상 commit 전 |
| **6 주차의 해소** | publishEvent 로 시간축 phase 분리. AFTER_COMMIT 이 양파 바깥보다도 더 바깥 |
| **`AfterReturning` advice 와 차이** | AOP `AfterReturning` 은 같은 트랜잭션 안 + 메서드 정상 종료 시점. `AFTER_COMMIT` 은 트랜잭션 commit 후 — 다른 시점 |
| **AOP + Event 함께** | 분산락 (AOP) + 트랜잭션 (AOP) + 감사 (AOP) + 알림 (Event) — 양파와 시간축이 직교 (orthogonal). 같이 쓰면 깔끔 |

## 🌟 7 주차 브릿지 — JPA / @DomainEvents

| 용어 | 풀어쓰면 |
|---|---|
| **`@DomainEvents`** | Spring Data JPA. Entity 메서드에 붙이면 Repository.save() 시 자동 발행 |
| **`AbstractAggregateRoot<T>`** | `@DomainEvents` + `@AfterDomainEventPublication` 미리 구현한 base. `registerEvent(event)` 한 줄로 발행 등록 |
| **Aggregate Root (DDD)** | 도메인 객체 군의 진입점. 외부에서는 Aggregate Root 만 참조 |
| **publisher 주입 불필요** | Entity 가 Spring 의존성 없이 순수. 도메인 객체에 어울림 |
| **JPA 영속성 컨텍스트** | 7 주차 본론. 또 다른 암묵적 메커니즘 — `EntityManager.persist` 가 즉시 INSERT 안 함, flush 시점에 |
| **AOP / Event / 영속성 컨텍스트** | 모두 컨테이너의 "암묵적 처리" 메커니즘. 결이 다름 — AOP (호출 가로채기) / Event (명시적 발행) / 영속성 (변경 감지 + 지연 쓰기) |

## ⚙️ 측정 / 비동기 도구

| 용어 | 풀어쓰면 |
|---|---|
| **`System.nanoTime()`** | 나노초 정밀도 시간. 비동기 효과 측정 |
| **`Thread.currentThread().getName()`** | 현재 스레드 이름. publisher / listener 가 같은 스레드인지 확인 |
| **`CountDownLatch`** | 비동기 리스너 완료 대기. 테스트 용도 |
| **`ConcurrentLinkedQueue`** | 비동기 환경에서 안전한 큐. 리스너가 처리한 이벤트 수집 |
| **`AtomicInteger`** | 락 없이 안전한 카운터 — 리스너 호출 횟수 측정 |
| **`@Async` 스레드명 prefix** | `executor.setThreadNamePrefix("event-")` → `event-1`, `event-2` 형태로 로그 추적 쉬움 |
| **부팅 시 비용** | `@EnableAsync` + executor 빈 등록 — 거의 없음 |
| **런타임 호출 비용** | publishEvent 호출 자체는 빠름 (μs 단위). 리스너 실행 시간이 본체 |
| **JIT 웜업** | 5 주차와 동일. 측정 전 5,000 회 이상 호출 |

## 🧪 학습 도구

| 용어 | 풀어쓰면 |
|---|---|
| **`record`** | Java 14+ 도입. payload-only 이벤트에 최적. 불변 + equals / hashCode 자동 |
| **`@Component`** | 리스너 클래스 Bean 등록. `@EventListener` 메서드가 인식되려면 필수 |
| **`spring-boot-starter`** | 6 주차는 별도 starter 불필요. 코어에 포함 |
| **`spring-boot-starter-jdbc`** | STAGE 2 트랜잭션 결합 학습 시. H2 인메모리 or 본인 5 주차 PostgreSQL |
| **H2 인메모리** | STAGE 2 학습용. `runtimeOnly 'com.h2database:h2'` |
| **`-parameters`** | 5 주차에서 익힘. SpEL `@EventListener(condition="#event.amount")` 안전장치 |
| **`SpringApplication.run()`** | Spring Boot 시작점. AsyncConfig / 리스너 빈 자동 등록 |
| **`@Target(ElementType.METHOD)`** | (이벤트 어노테이션 자작 시) 메서드에만 |
| **`@Retention(RetentionPolicy.RUNTIME)`** | 런타임까지 살아있어야 Spring 이 인식 |

---

## ★ STAGE 1 진입 관문 (입으로 답)

1. **5 주차 양파 한계** — `@Order(1) TX + @Order(2) Audit` 에서 Audit 가 commit 전 발사 → 외부 시스템 회수 불가
2. **`@TransactionalEventListener` 4 phase** — BEFORE_COMMIT (같은 트랜잭션 마지막) / AFTER_COMMIT (외부 알림) / AFTER_ROLLBACK (보상) / AFTER_COMPLETION (정리)
3. **`@Async` self-invocation** — `@Transactional` 과 같은 프록시 메커니즘. `this` = 원본 → 프록시 우회 → 비동기 X

## ★ STAGE 2 진입 관문 (6 주차 가장 중요)

1. **`@EventListener` 만 vs `@TransactionalEventListener(AFTER_COMMIT)`** — rollback 시 동작 차이 + 이유
2. **`fallbackExecution`** — 트랜잭션 밖 publishEvent 시 기본 동작 + true 설정 효과
3. **`TransactionSynchronizationManager`** — 5 주차 STAGE 2-1 의 ThreadLocal `TX_CONN` 의 실무 추상화. `@TransactionalEventListener` 가 콜백 등록하는 자리
