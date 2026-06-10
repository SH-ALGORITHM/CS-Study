# 측정 기록 (6주차 — Spring Event)

도메인: **결제(payment)**. `MeasurementLog.save(stage, note)` 로 각 STAGE 관찰을 자동 누적.
아래 `- [시각] sX-Y · ...` 항목은 코드 실행 시 자동 기록되고, 해석 메모는 직접 덧붙인다.

## STAGE 1 — publishEvent + @EventListener (직접 관찰)

`ApplicationEventPublisher` 가 자동으로 해주는 발행/분배를 가장 작은 단위부터 손으로 확인.

### 자동 누적 로그

- [06-08 19:50] s1-1 · HelloEvent 발행 → 동기 리스너 호출 순서 println 확인
- [06-08 19:54] s1-2 · @Order로 리스너 호출 순서 제어 확인 (작은 값 먼저)
- [06-08 20:03] s1-3 · 동기 기준동작: 예외 전파 O, 체인 중단 O — STAGE3 @Async 면 전파 X 예정
- [06-08 20:10] s1-4 · ApplicationListener<E> vs @EventListener — 동작 동일(같은 메커니즘), 
  단 인터페이스는 클래스당 이벤트 1개 고정
- [06-08 20:24] s1-5 · payload-only — ApplicationEvent 상속없이 String/record 발행, 타입으로 매칭 (내부 PayloadApplicationEvent 래핑)

| 단계 | 한 줄 결론 |
|---|---|
| 1-1 | 리스너는 publisher 와 **같은 스레드에서 동기 호출** (`return` 이 리스너 뒤에 출력) |
| 1-2 | 호출 순서는 `@Order` 숫자가 결정 (작을수록 먼저, 선언/이름 무관) |
| 1-3 | 동기일 때 리스너 예외 → **다음 리스너 중단 + 호출자까지 전파** (STAGE 3 @Async 면 달라짐) |
| 1-4 | `ApplicationListener<E>` 인터페이스(= `ApplicationEvent` 상속 필요) vs `@EventListener`(불필요) — 동작은 같은 메커니즘 |
| 1-5 | payload-only: 상속 없이 String/record 발행, **payload 타입으로 매칭** (내부 `PayloadApplicationEvent` 래핑) |


## STAGE 2 — `@TransactionalEventListener` 4 phase

5주차 advice 안-밖(@Audited 가 commit 전 실행)의 한계를 코드로 재현하고,
시간축 phase 로 "발행 시점"과 "처리 시점"을 commit 기준으로 분리한다.

### 자동 누적 로그

- [06-10 17:51] s2-1 · 순진한 @EventListener — 롤백돼도 알림 이미 전송됨(함정 재현)
- [06-10 17:55] s2-1 · @TransactionalEventListener(AFTER_COMMIT) — 롤백 시 알림 호출 X 확인
- [06-10 18:03] s2-2 · 4 phase 매트릭스 — commit/rollback 별 호출 phase 관찰
- [06-10 18:25] s2-4 · 트랜잭션 밖 publishEvent — fallbackExecution false/true 비교
- [06-10 18:25] s2-4 · 트랜잭션 밖 publishEvent — fallbackExecution false/true 비교
- [06-10 18:55] s2-5 · BEFORE_COMMIT 같은 트랜잭션 쓰기 + AFTER_COMMIT DB쓰기 함정/REQUIRES_NEW
- [06-10 18:56] s2-5 · BEFORE_COMMIT 같은 트랜잭션 쓰기 + AFTER_COMMIT DB쓰기 함정/REQUIRES_NEW
- [06-10 18:57] s2-5 · BEFORE_COMMIT 같은 트랜잭션 쓰기 + AFTER_COMMIT DB쓰기 함정/REQUIRES_NEW
- [06-10 18:59] s2-5 · BEFORE_COMMIT 같은 트랜잭션 쓰기 + AFTER_COMMIT DB쓰기 함정/REQUIRES_NEW

### 2-1 — 순진한 `@EventListener` 함정 → `AFTER_COMMIT` 해결

`PaymentService.complete()` 가 INSERT → `publishEvent` → 음수면 예외(롤백).
같은 코드에서 리스너 어노테이션 한 줄만 바꿔 두 버전 비교.

| 케이스 | `@EventListener` (순진) | `@TransactionalEventListener(AFTER_COMMIT)` |
|---|---|---|
| 정상 commit (id=1) | 알림 ✓ (commit **전**, publishEvent 순간) | 알림 ✓ (commit **후**) |
| 예외 → rollback (id=2) | **알림 ✗ 이미 전송됨** (회수 불가) | 알림 **안 나감** ✓ |
| payment 행 수 | 1 (id=2 INSERT 는 롤백) | 1 (동일) |

**해석**:
- `@EventListener` 는 트랜잭션을 모름 → `publishEvent` 순간 같은 스레드에서 즉시 실행.
  롤백돼도 "결제 완료" 알림이 이미 나가는 모순 (DB 행은 1개인데 알림은 2번).
- `@TransactionalEventListener(AFTER_COMMIT)` 는 발행 시점엔 현재 트랜잭션에
  **콜백만 등록**(`TransactionSynchronizationManager.registerSynchronization`),
  commit 성공 시에만 실행. 롤백 시 콜백 자체가 버려짐 → 자동 취소.
- 정상 케이스에서도 알림 출력 시점이 메서드 return 이후(commit 후)로 밀림 = 시간축 분리.

### 2-2 — 4 phase 한 이벤트에 다 붙여 commit/rollback 매트릭스

한 `complete()` 가 발행한 이벤트 하나를 4개 phase 리스너가 받음. 정상/롤백 각각 호출 phase 관찰.

| phase | 정상 commit | 예외 → rollback |
|---|---|---|
| `BEFORE_COMMIT` | ✓ | ✗ (commit 시도 자체를 안 함) |
| `AFTER_COMMIT` | ✓ | ✗ (status≠COMMITTED 가드) |
| `AFTER_ROLLBACK` | ✗ | ✓ |
| `AFTER_COMPLETION` | ✓ | ✓ (항상) |

**phase → 내부 콜백 매핑**:
- `BEFORE_COMMIT` → `TransactionSynchronization.beforeCommit()` (commit 직전에만)
- `AFTER_COMMIT` / `AFTER_ROLLBACK` / `AFTER_COMPLETION` → **모두 같은 `afterCompletion(status)`** 안에서 status 가드로 분기

**실측 발견 (★ 문서 반박)**:
- 출력 순서가 시나리오 예상과 다름 — 정상: `BEFORE_COMMIT` → **`AFTER_COMPLETION` → `AFTER_COMMIT`** (예상은 COMMIT 먼저).
  롤백: `AFTER_COMPLETION` → `AFTER_ROLLBACK`.
- 이유: 위 셋이 같은 `afterCompletion()` 콜백 안에서 처리됨 → **상대 순서는 phase 의미가 보장 X**,
  synchronization 등록 순서로 결정(=`@Order` 없으면 사실상 비결정적).
- 결론: "phase 는 *언제 그룹이 실행되는지(commit/rollback 기준)* 만 보장. 같은 after-그룹 *내부 순서*는 의존 금지."

### 2-3 — phase 배치 결정 (결제 도메인)

| 리스너 | phase | 근거 |
|---|---|---|
| 영수증 발급 | `BEFORE_COMMIT` | 결제와 정합성 묶임(도메인 가정) — 실패 시 결제도 롤백 |
| 포인트 적립 | `AFTER_COMMIT` | 결제 성공과 운명 분리 — 실패해도 결제 유지, 재시도 |
| 결제 실패 보상 | `AFTER_ROLLBACK` | 롤백 시에만 정리/통보 |
| 감사 로그 | `AFTER_COMPLETION` | 성공/실패 무관 항상 기록 |

**결정 기준 (★)**: "DB 쓰기냐 아니냐"가 아니라 **"이게 실패하면 결제를 무효로 만들어야 하나(=원자성 필요)"**.
- 무효로 만들어야 함 → 같은 트랜잭션 `BEFORE_COMMIT` (실패 시 본 트랜잭션 롤백). 예: 회계 원장 분개
- 무효로 만들면 안 됨 → `AFTER_COMMIT` (운명 분리, 별도 재시도). 예: 포인트 적립
- 영수증은 경계 케이스 — 도메인이 "영수증 정합성 필수"로 정의하면 BEFORE_COMMIT.

### 2-4 — fallbackExecution: 트랜잭션 밖 publishEvent

`PaymentAuditService.recordAttempt()` 는 `@Transactional` 없음(트랜잭션 밖 발행).

| 설정 | 결과 | 타이밍 |
|---|---|---|
| `fallbackExecution=false` (기본) | 리스너 **조용히 무시** (WARN 조차 없음 = 함정) | — |
| `fallbackExecution=true` | 즉시 실행 (그냥 `@EventListener` 처럼) | publisher return **전**, 인라인 같은 스레드 |

**해석**: 트랜잭션이 없으면 "commit 후" 기준점 자체가 없음 → 기본값 false 는 등록할 콜백 자리가
없어 침묵. 트랜잭션 없는 경로(인증/조회)에서도 리스너를 재사용하려면 `true` 명시 필요.

### 2-5 — BEFORE_COMMIT 같은 트랜잭션 쓰기 + AFTER_COMMIT DB쓰기 함정 (REQUIRES_NEW)

`settle()` → `PaymentSettledEvent`. ReceiptListener(BEFORE_COMMIT)=receipt INSERT,
PointListener(AFTER_COMMIT)=point INSERT.

| 실험 | 결과 |
|---|---|
| ReceiptListener BEFORE_COMMIT | receipt 행 = 1 — 본 트랜잭션과 **같이 commit** (같은 DB 쓰기는 BEFORE_COMMIT 자연) |
| PointListener AFTER_COMMIT, **REQUIRES_NEW 없이** | point 행 = **1** (내 H2 에선 유실 안 됨) |
| PointListener AFTER_COMMIT, **REQUIRES_NEW 있음** | point 행 = 1 (안정) |

**★ 핵심 발견 — "되던데?"가 제일 위험한 함정**:
- 내 환경(H2 2.2.224 + Boot 3.2 JDBC)에선 REQUIRES_NEW 없이도 point=1 로 살아남음.
- **왜 살아남나(메커니즘)**: 본 트랜잭션 begin 시 커넥션 `autoCommit` 을 true→false 로 바꿔둠.
  AFTER_COMMIT 리스너가 아직 바인딩된 같은 커넥션으로 INSERT(묶일 트랜잭션 없음).
  이후 cleanup 이 `autoCommit` 을 false→true 로 복구하는데, **JDBC 스펙상 `setAutoCommit(true)` 는
  pending 트랜잭션을 commit** → orphan INSERT 가 **우발적으로** commit 됨.
- **그래서 REQUIRES_NEW 여전히 필수**:
  - 이식성 없음 — 드라이버/풀/버전의 setAutoCommit 구현에 기댄 commit. PostgreSQL/다른 풀이면 유실 가능.
  - JPA 면 그냥 유실 — `persist()` 는 staging 만, flush 는 이미 지난 commit 시점 → autoCommit 트릭으로도 안 살아남.
  - 관리 안 되는 "유령 commit" — 롤백 불가, 트랜잭션 경계 없음.
- 결론: AFTER_COMMIT DB 쓰기엔 `@Transactional(propagation=REQUIRES_NEW)` 명시 → 새 트랜잭션의
  자기 commit 으로 환경 무관 안전.

> 면접 차별점: 대부분 "0 나옵니다"만 앎. "1 나와도 위험한 이유(우발적 commit·비이식성·JPA 유실)"까지가 답.


## STAGE 3 — `@Async` 비동기

### 3-1/3-2 — 동기 vs @Async

리스너 안에 `Thread.sleep(500)`. publisher 시간을 `System.nanoTime()` 으로 측정.

| 모드 | publisher 총 시간 | `[slow]` 스레드 |
|---|---|---|
| 동기 `@EventListener` | **511ms** | main |
| `@Async` | **4ms** | task-N |

**해석**: 동기는 리스너가 publisher 와 같은 스레드 → 500ms 가 그대로 더해짐(511). `@Async` 는
리스너를 별 스레드(task-N)로 던지고 publisher 즉시 반환(4ms) → 리스너 처리시간과 **디커플**.
HTTP 요청 처리면 응답시간에 직결되므로 비동기가 치명적 차이.

### 3-3 — self-invocation 함정 (5주차 @Transactional 회수)

`place()` 안에서 `this.asyncNotify()` 호출 → `[asyncNotify] thread=main` (비동기 무시, 동기 실행).

- **메커니즘**: `@Async` advice 는 프록시에 있음. `this` 는 원본 target(프록시 아님) → 프록시 우회 → advice 안 걸림. 5주차 `@Transactional` self-invocation 과 동일.
- **해결**: (a) self 주입 / (b) 클래스 분리 / (c) ★ 이벤트 발행 → 별도 `@Async` 리스너가 받음
  (publishEvent 는 멀티캐스터→리스너 프록시 경유라 `@Async` 적용 + 자연 분리. 가장 6주차다운 해법).
- 비동기 도구(가상 스레드 포함)가 바뀌어도 프록시 이슈라 그대로 남음.

### 3-6 — @Async + AFTER_COMMIT 새 스레드 함정 (REQUIRES_NEW)

`settle()`(main, tx) → 발행 → `@Async @TransactionalEventListener(AFTER_COMMIT)` 리스너.

| 리스너 트랜잭션 | thread | actualTxActive | syncActive |
|---|---|---|---|
| REQUIRES_NEW 없음 | task-1 | **false** | **false** |
| `REQUIRES_NEW` 있음 | task-1 | **true** | **true** |

**해석**: `@Async` 가 AFTER_COMMIT 콜백을 별 스레드(task-1)로 던짐 → `TransactionSynchronizationManager`
(ThreadLocal, 5주차 회수)가 그 스레드엔 안 따라옴 → 트랜잭션 컨텍스트 없음(false/false).
JPA 면 영속성 컨텍스트도 없어 Lazy 로딩 시 `LazyInitializationException`. `REQUIRES_NEW` 가 새 스레드에서
새 트랜잭션을 열어 컨텍스트 확보(true/true). → `@Async + AFTER_COMMIT` 에서 DB/영속성 만지면 REQUIRES_NEW 필수.
2-5 의 REQUIRES_NEW 함정 + 새 스레드 ThreadLocal 손실이 겹치는 자리. 7주차 JPA 단골.

### 3-4 / 3-5 / 3-7 — 개념 정리 (미실측)

**3-4 스레드풀 함정**: Boot 자동 `applicationTaskExecutor` 기본 = core=8 / queue=MAX / max=MAX.
- `ThreadPoolExecutor` 증설 규칙: core 채움 → **큐 채움** → 큐 다 차야 max 증설 → max 차면 RejectedHandler.
- 큐가 무제한이라 영원히 안 참 → max 증설 불가 → **사실상 8개 고정 + 무한 큐**. 직접 설정 시 queue 유한값(예 100)으로 막아야 max 증설이 진짜 일어남.
- 멀티캐스터 전역 비동기 vs `@Async`: 전역은 모든 리스너 비동기 + phase 콜백 ThreadLocal 동기화와 어긋남 → **per-listener `@Async` 가 정답**.

**3-5 @Async 예외**: 반환 `void` → 예외 **조용히 소멸**(publisher 전파 X, 1-3 동기와 정반대).
`Future<T>` → `get()` 시 `ExecutionException`. `AsyncUncaughtExceptionHandler` 빈으로 void 예외도 포착 가능.

**3-7 Virtual Thread**: `spring.threads.virtual.enabled=true` 한 줄 → 자동 executor 가상 스레드化.
I/O 바운드면 풀 튜닝 고민 소멸. 검증은 이름(task-N 유지) 말고 `Thread.currentThread().isVirtual()`.
self-invocation 함정은 가상 스레드와 **직교**(프록시 이슈라 그대로).

### 자동 누적 로그

- [06-11 01:34] s3-1 · publisher 블록 시간 = 511ms
- [06-11 01:35] s3-1 · publisher 블록 시간 = 4ms
- [06-11 01:37] s3-3 · self-invocation — this.asyncMethod() 는 @Async 무시(동기)
- [06-11 01:40] s3-6 · @Async + AFTER_COMMIT 새 스레드 — 트랜잭션 컨텍스트 유무
- [06-11 01:40] s3-6 · @Async + AFTER_COMMIT 새 스레드 — 트랜잭션 컨텍스트 유무
