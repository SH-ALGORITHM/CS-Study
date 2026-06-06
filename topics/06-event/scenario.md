# 6주차 — 명시적으로 이벤트를 발행해서 commit 후 처리까지 (Spring Event)

이번 주제: 5 주차에 `@Audited` 같은 AOP 가 메서드 호출을 가로채서 공통 관심사를 끼워넣었다. 그런데 **`@Order(1) @Transactional` + `@Order(2) @Audited` advice 안-밖 구조에서 감사 로그는 commit 직전 (안쪽) 에 실행됨** — 만약 감사 코드가 "외부 알림 / 결제 PG / 이메일" 처럼 **롤백되면 안 되는 외부 부수 효과** 라면, 트랜잭션이 롤백돼도 이미 전송되어버린다. 6 주차는 이걸 "암묵적 가로채기 (AOP)" 대신 **"명시적 이벤트 발행 + commit 후 리스너"** 로 푸는 메커니즘을 다룬다. `ApplicationEventPublisher`, `@EventListener`, `@TransactionalEventListener(phase = AFTER_COMMIT)`, `@Async` 까지.

5 가지 학습 축:
- `ApplicationEventPublisher` + `@EventListener` — 명시적 발행 / 구독. 가장 작은 단위부터 손으로
- `@TransactionalEventListener` 의 4 phase — `BEFORE_COMMIT` / `AFTER_COMMIT` / `AFTER_ROLLBACK` / `AFTER_COMPLETION`. 5 주차 advice 안-밖 순서의 한계에 대한 정답
- `@Async` + `ThreadPoolTaskExecutor` — 동기 한계 (리스너 느리면 publisher 도 느림) → 별 스레드. Boot 자동 executor 의 "max=무제한" 함정 + Java 21 Virtual Thread 까지
- self-invocation 함정 (5 주차 회수) — `@Async` 도 같은 프록시 메커니즘. `@Async + AFTER_COMMIT` 의 새 스레드 함정 (ThreadLocal / 영속성 컨텍스트 날아감 → `REQUIRES_NEW`)
- AOP vs Event — 암묵적 가로채기 vs 명시적 발행. 어느 쪽을 언제 쓰는가

---

## 우선 알아둬야 할 단어 (시작 전 1 분)

| 단어 | 풀어쓰면 |
|---|---|
| **이벤트 (Event)** | "어떤 일이 일어났다" 라는 사실을 표현하는 객체. 과거형 이름 (`OrderPlacedEvent`, `TransferCompletedEvent`) |
| **`ApplicationEventPublisher`** | 스프링이 제공하는 이벤트 발행 인터페이스. `publishEvent(event)` 한 줄 |
| **`@EventListener`** | 이벤트 리스너 어노테이션. 메서드에 붙임. 기본 **동기** (같은 스레드) |
| **`ApplicationListener<E>`** | 리스너 인터페이스 (옛 방식). `@EventListener` 가 더 권장 |
| **payload-only 이벤트** | Spring 4.2+ 부터 `ApplicationEvent` 상속 불필요. POJO / `record` 도 OK |
| **`@TransactionalEventListener`** | 트랜잭션 상태 기반 리스너. 4 phase 중 하나 선택 |
| **`TransactionPhase`** | `BEFORE_COMMIT` / `AFTER_COMMIT` (기본) / `AFTER_ROLLBACK` / `AFTER_COMPLETION` |
| **`@Async`** | 메서드 / 리스너를 별 스레드에서 실행. `@EnableAsync` 필요 |
| **`ThreadPoolTaskExecutor`** | `@Async` 가 쓰는 스레드풀. core / max / queue 사이즈 직접 설정 |
| **`SimpleApplicationEventMulticaster`** | 이벤트를 리스너에 분배하는 내부 객체. 기본 동기, `setTaskExecutor` 로 비동기화 |
| **publish-subscribe** | 발행자 / 구독자 분리 패턴. publisher 는 리스너를 모름 → 결합도 낮음 |
| **fallbackExecution** | `@TransactionalEventListener(fallbackExecution=true)` — 트랜잭션 밖에서 호출 시 즉시 실행 (기본은 무시) |
| **Virtual Thread** (Java 21) | `spring.threads.virtual.enabled=true` 한 줄로 비동기 풀 튜닝 고민 사라짐 (I/O 바운드). Boot 3.2+ |
| **Transactional Outbox** | `AFTER_COMMIT + @Async` 의 at-most-once 한계 보완. DB outbox 테이블 → MQ 발행 → at-least-once |
| **`@DomainEvents`** (Spring Data JPA) | Aggregate Root 에서 자동 발행. 7 주차 브릿지 |

> 📚 더 깊은 용어 (4 phase 각각 / `@Async` 예외 처리 / `Multicaster` 내부 / Event vs Message Queue 등) — [`terms.md`](terms.md) 참고. 5 주차와 같은 형식, 카테고리별 정리.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 0. 개념 숙지 (도메인 무관, 모두 동일)
━━━━━━━━━━━━━━━━━━━━━━━━━━

## 필수 개념 (AI 와 대화하며 숙지)

### 5 주차 한계 → 6 주차 동기
1. **5 주차 advice 안-밖 (@Order(1) TX + @Order(2) Audit) 의 한계** — Audit 가 commit 직전 (TX 안쪽) 실행. 만약 audit 이 외부 알림 / 결제 PG / 이메일이면 **트랜잭션 롤백돼도 이미 전송됨**. 학습자 본인이 5 주차에 직접 본 그 흐름
2. **"안쪽 advice 를 바깥쪽으로 빼면 되지 않나?"** — 안 됨. advice 안-밖 구조에서 안쪽이 TX 안. 바깥으로 빼려면 TX advice 자체가 안쪽이어야 하는데 그러면 commit 보장이 안 됨. 구조적 한계
3. **해법** — 메서드 안에서 `publisher.publishEvent(new XxxEvent())` 한 줄 → 리스너가 **commit 후 (AFTER_COMMIT)** 에 실행. 트랜잭션이 롤백되면 리스너 호출도 자동 취소

### Event 패턴 본질
4. **publish-subscribe 패턴** — 발행자 (publisher) 는 누가 듣는지 모름. 구독자 (listener) 만 본인이 어느 이벤트 받을지 결정. 결합도 낮춤
5. **이벤트는 과거형** — `OrderPlacedEvent` / `TransferCompletedEvent` / `UserRegisteredEvent`. "이미 일어난 일" 을 표현. 동사 / 명령 (Place / DoTransfer) 금지
6. **payload-only (Spring 4.2+)** — `ApplicationEvent` 상속 의무 사라짐. `record OrderPlacedEvent(Long orderId, Long userId, BigDecimal amount) {}` 로 충분
7. **여러 리스너** — 한 이벤트에 리스너 N 개 가능. `@Order` 로 호출 순서 명시. 한 리스너에서 예외 던지면 (동기일 때) 다음 리스너 호출 안 됨

### `ApplicationEventPublisher` 메커니즘
8. **`publisher.publishEvent(event)` 의 실제 동작** — 컨테이너의 `SimpleApplicationEventMulticaster` 가 받음 → 이벤트 타입에 매칭되는 모든 `ApplicationListener` 를 찾아서 순차 호출
9. **`@EventListener` 가 어떻게 listener 로 등록되나** — `EventListenerMethodProcessor` (4 주차 `internal*` 5 개 중 하나) 가 Bean 초기화 시점에 `@EventListener` 메서드를 찾아서 `ApplicationListenerMethodAdapter` 로 감싸 등록

### `@TransactionalEventListener` 4 phase
10. **4 phase 가 푸는 문제** — 단순 `@EventListener` 는 **트랜잭션 무관** → publisher 가 commit 하기 전에 리스너가 실행됨. 외부 알림이 트랜잭션 롤백 후에도 전송된 상태로 남음. `@TransactionalEventListener` 는 현재 트랜잭션에 콜백을 등록 → phase 시점에만 실행
11. **각 phase 언제 쓰나** —
    - `BEFORE_COMMIT` — commit 직전. 마지막 검증 / 같은 트랜잭션 안에서 추가 쓰기
    - `AFTER_COMMIT` (기본) — commit 직후. 알림 / 외부 API / 이메일 (롤백 가능성 없음)
    - `AFTER_ROLLBACK` — rollback 직후. 보상 처리 (실패 통보, 캐시 정리)
    - `AFTER_COMPLETION` — commit / rollback 무관, 트랜잭션 종료 후. 정리 / 로깅
12. **트랜잭션 밖에서 publishEvent 하면?** — 기본은 **무시** (콜백 등록할 트랜잭션이 없음). `fallbackExecution = true` 면 즉시 실행

### `@Async` 비동기
13. **동기의 한계** — 기본 `@EventListener` 는 publisher 와 **같은 스레드**. 리스너 5 개 × 각 200ms 면 publisher 도 1s 동안 블록. HTTP 요청 처리에서 치명적
14. **`@Async` + `@EnableAsync`** — 리스너 메서드에 `@Async` 붙이면 별 스레드. publisher 는 즉시 반환. 단 **반환값 / 예외가 사라짐** (Future 없으면)
15. **self-invocation 함정** (5 주차 회수) — `@Async` 도 프록시 메커니즘. 같은 클래스 안에서 `this.asyncMethod()` 호출하면 비동기 X (동기로 실행). `@Transactional` 함정과 정확히 동일
16. **`ThreadPoolTaskExecutor` 설정** — `corePoolSize` / `maxPoolSize` / `queueCapacity` 직접 지정. 안 하면 기본 `SimpleAsyncTaskExecutor` (요청마다 새 스레드 — 위험)

### AOP vs Event
17. **결정 매트릭스** — 같은 도메인 / 같은 클래스의 횡단 관심사 (로깅 / 측정) = AOP. 다른 도메인 / 다른 모듈로 부수 효과 전파 (알림 / 통계 / 외부 API) = Event. 둘 다 가능한 회색지대 (감사 로그) 는 commit 보장 필요 여부로 결정
18. **Event 가 AOP 의 advice 순서 한계를 푸는 방식** — AOP 는 advice 순서가 안 / 밖 구조. Event 는 phase 가 시간축 (commit 전 / 후 구조). advice 안에 갇혀있던 호출을 시간축 밖으로 빼냄

## 자기 검증 (입으로 답할 수 있어야 STAGE 1 시작)

**★ 관문 — 이 3 개는 입으로 답해야 STAGE 1 진입**
- [ ] ★ 5 주차 @Order advice 안-밖 (TX 바깥 + Audit 안쪽) 의 한계 — 1 분 본인 말로. 그리고 6 주차가 어떻게 푸는지 1 줄
- [ ] ★ `@TransactionalEventListener` 4 phase 각각 언제 쓰나
- [ ] ★ `@Async` self-invocation 이 안 먹는 이유 — `@Transactional` 함정과 같은가 다른가

**보너스 — STAGE 1 진행하면서 답할 수 있게**
- [ ] `publisher.publishEvent(event)` 한 줄이 컨테이너 내부에서 일어나는 일 (3 단계)
- [ ] `@EventListener` 와 `ApplicationListener<E>` 인터페이스 직접 구현 — 무엇이 다른가
- [ ] 트랜잭션 밖에서 `publishEvent` 호출 시 `@TransactionalEventListener` 동작 + `fallbackExecution=true` 효과
- [ ] `@Async` + `ThreadPoolTaskExecutor` — 기본 executor 가 위험한 이유 (스레드 누수)
- [ ] AOP vs Event 결정 매트릭스 — 본인 도메인 메서드 1 개로 결정 근거 설명
- [ ] 5 주차 자작 `@Audited` (AOP) 를 6 주차 `@EventListener` 로 옮기면 어떤 코드가 어떻게 바뀌나
- [ ] Spring Boot 자동 `applicationTaskExecutor` 기본값 (core=8 / queue=MAX / max=MAX) 가 사실상 8 개 고정인 이유
- [ ] 멀티캐스터 전역 비동기 vs `@Async` 리스너 — 왜 전자는 phase 와 충돌하나
- [ ] `AFTER_COMMIT` + `@Async` 의 at-most-once 한계 + 보장 필요 시 (Transactional Outbox + MQ)
- [ ] `AFTER_COMMIT` 리스너에서 DB 쓰기가 조용히 무시될 수 있는 이유 + `REQUIRES_NEW`
- [ ] Java 21 + Boot 3.2 `spring.threads.virtual.enabled=true` 효과 + self-invocation 함정과의 직교성
- [ ] 7 주차 `@DomainEvents` 예고 — JPA Entity 가 publisher 없이 이벤트 발행하는 메커니즘


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 1. 본인 도메인 선택 — 6 주차에 맞게 (부수 효과 자연 + commit 후 처리 필요)
━━━━━━━━━━━━━━━━━━━━━━━━━━

6 주차 학습 포인트 (**publishEvent / 4 phase / @Async / AOP vs Event**) 는 **메서드 한 번에 여러 부수 효과 (알림 / 감사 / 통계 / 외부 시스템) 가 자연스러운 도메인** 에서 잘 드러난다. 5 주차의 AOP 도메인 (감사 로그 / 캐싱 등) 과는 결이 다르다 — "한 곳에서 끼워넣기" 가 아니라 "한 사건이 여러 모듈로 퍼지기".

## 옵션 — 5 주차 도메인 그대로 vs 새 도메인

| 옵션 | 권장 대상 | 흐름 |
|---|---|---|
| **A. 5 주차 도메인 그대로 + 이벤트로 부수 효과 전파** | 도메인 새로 짜기 부담스러운 사람 | 5 주차 @Audited / @Timed 같은 AOP 적용 메서드의 부수 효과 일부를 6 주차 `@EventListener` 로 옮김. 자연스러운 비교 |
| **B. 새 도메인 선택** | 6 주차 학습 본격 | STEP 1 후보표에서 부수 효과 ★★★ 도메인 (주문 / 결제 / 회원가입 등) 선택 |
| **C. 혼합** | 가장 무난 | STAGE 1 (publishEvent 손맛) 까지 공통 학습 도메인 (예: 주문) → STAGE 2 ~ 4 본인 5 주차 도메인 연장 |

**모두 STAGE 1 (publishEvent + @EventListener 가장 작은 단위 손 작성) 은 공통.** 본인 도메인 무관.

## 후보 도메인 + 적합도 (12 개 — 7 명이 1 개씩 + 여유 5)

| # | 도메인 | 부수 효과 자연 | 트랜잭션 자연 | 면접 가치 | 메모 |
|---|---|---|---|---|---|
| 1 | **주문 완료** (`order`) | ★★★ | ★★★ | ★★★ | 결제 / 재고 / 알림 / 통계 / 슬랙. **가장 정석**. Spring 공식 가이드 표준 |
| 2 | **결제 완료** (`payment`) | ★★★ | ★★★ | ★★★ | 영수증 / 포인트적립 / 감사 / 외부 PG 콜백. `@TransactionalEventListener` 의 정석 |
| 3 | **회원가입** (`signup`) | ★★★ | ★★ | ★★★ | 환영메일 / 쿠폰지급 / 통계 / CRM 동기화 |
| 4 | **송금 완료** (`transfer`) | ★★★ | ★★★ | ★★★ | **5 주차 chanhyeok 연장**. 5 주차 @Audited 가 commit 전 실행 → 6 주차 AFTER_COMMIT 로 옮김 |
| 5 | **파일 업로드** (`file_upload`) | ★★★ | ★★ | ★★ | 썸네일 생성 / 검색 인덱싱 / 알림. `@Async` 가 강하게 필요 |
| 6 | **게시글 작성** (`post`) | ★★ | ★★ | ★★ | 알림 / 검색 인덱싱 / 통계. 입문자용 |
| 7 | **댓글 작성** (`comment`) | ★★★ | ★★ | ★★ | 글쓴이 알림 / mention 알림 / 통계 |
| 8 | **로그인** (`login`) | ★★ | ★ | ★★ | 감사 / 보안 / 통계. 트랜잭션 없는 케이스 (`fallbackExecution`) 학습 |
| 9 | **좋아요** (`like`) | ★★ | ★★ | ★ | 통계 / 알림. 가장 단순 |
| 10 | **재고 변경** (`inventory`) | ★★★ | ★★★ | ★★★ | 부족 알림 / 외부 시스템 동기화 / 보상 (롤백 시) — `AFTER_ROLLBACK` 자연 |
| 11 | **상품 등록** (`product`) | ★★ | ★★ | ★★ | 검색 인덱스 / 캐시 무효화 / 알림 |
| 12 | **회원 탈퇴** (`withdrawal`) | ★★★ | ★★★ | ★★★ | 데이터 정리 / 외부 시스템 통보 / 보상 처리 (`AFTER_ROLLBACK`) — 4 phase 다 등장 |

> **부수 효과 ★★★ 조건** = 한 사건 (메서드 1 회 실행) 으로 인해 자연스럽게 2 ~ 4 가지 부수 처리가 필요 + 각각이 다른 모듈 / 다른 책임. 이벤트 발행의 본질이 잘 드러남.

## 도메인별 예상 시나리오 한 줄

| # | 한 줄 시나리오 |
|---|---|
| 1 | `OrderService.placeOrder()` 끝에 `publishEvent(new OrderPlacedEvent(...))`. 4 개 리스너 (재고 차감 / 알림 / 통계 / 슬랙) 가 AFTER_COMMIT 으로 받음 |
| 2 | `PaymentService.complete()` 가 `PaymentCompletedEvent` 발행. 영수증 발급 (BEFORE_COMMIT) / 포인트 적립 (AFTER_COMMIT) / 실패 시 보상 (AFTER_ROLLBACK) |
| 3 | `SignupService.register()` 가 `UserRegisteredEvent` 발행. 환영메일 / 쿠폰 지급 / 통계 — 모두 AFTER_COMMIT + @Async |
| 4 | 5 주차 `TransferService.transfer()` 의 `@Audited` 를 떼고, `publishEvent(new TransferCompletedEvent(...))` 로. AuditListener 가 AFTER_COMMIT |
| 5 | `FileUploadService.upload()` 가 `FileUploadedEvent` 발행. ThumbnailGenerator / SearchIndexer / Notifier 가 @Async 로 병렬 처리 |
| 6 | `PostService.write()` → `PostWrittenEvent`. 팔로워 알림 / 검색 인덱스 / 통계 분배 |
| 7 | `CommentService.write()` → `CommentWrittenEvent`. 글쓴이 알림 + @mention 파싱 후 mention 알림 |
| 8 | `LoginService.login()` → `LoggedInEvent`. 트랜잭션 안 / 밖 모두 발행 가능 → `fallbackExecution=true` 학습 |
| 9 | `LikeService.like()` → `LikedEvent`. 글 카운터 증가 / 글쓴이 알림 |
| 10 | `InventoryService.decrement()` → `InventoryChangedEvent`. 부족 시 알림 / 외부 시스템 동기화 / 실패 시 보상 |
| 11 | `ProductService.register()` → `ProductRegisteredEvent`. 검색 인덱스 / 캐시 무효화 / 신상품 알림 |
| 12 | `WithdrawalService.withdraw()` → `UserWithdrewEvent`. 본인 데이터 정리 (BEFORE_COMMIT) / 외부 시스템 통보 (AFTER_COMMIT) / 실패 시 복구 (AFTER_ROLLBACK) — **4 phase 다 등장하는 유일 도메인** |

## 학습자 프로필별 추천

| 본인 상황 | 추천 도메인 |
|---|---|
| Event 처음 / 입문자 | **9 좋아요** 또는 **6 게시글** — 부수 효과 2 ~ 3 개로 작게 시작 |
| 5 주차 advice 안-밖 순서의 한계 직접 해소 | **4 송금** (5 주차 chanhyeok 연장) — @Audited → AFTER_COMMIT 으로 옮김 |
| 면접 가치 최대화 | **1 주문** / **2 결제** / **10 재고** / **12 회원탈퇴** |
| 4 phase 다 다뤄보기 | **12 회원탈퇴** — BEFORE_COMMIT / AFTER_COMMIT / AFTER_ROLLBACK / AFTER_COMPLETION 다 등장 |
| `@Async` + 스레드풀 중점 | **5 파일 업로드** — 썸네일 생성이 무거움 → 비동기 필수 |
| 트랜잭션 밖 이벤트 (`fallbackExecution`) 학습 | **8 로그인** — 인증 자체는 트랜잭션 없음 |
| 4 주차 / 5 주차 도메인 연장 | **옵션 A** — 본인 도메인 메서드 + 이벤트 발행 한 줄 추가 |
| 7 주차 (JPA) 자연스러운 브릿지 | **1 주문** / **2 결제** — `@DomainEvents` (Aggregate Root) 가 가장 자연 |

## 5 주차 도메인이 약한 이유 (참고)

5 주차에 도메인을 "공통 관심사 (감사 / 측정 / 캐싱)" 중심으로 골랐다면, 6 주차 학습 포인트 (한 사건 → 여러 모듈 분배) 가 안 보임. 감사 로그는 한 곳에서 자동으로 끼우는 게 본질 → AOP 가 적합. 6 주차는 부수 효과가 **다른 모듈로 명시적으로 퍼져나가는** 도메인이 필요.

→ 5 주차에 캐싱 / 로깅 / Rate Limit 같은 횡단 관심사 도메인 출신은 6 주차에 **새 도메인** (주문 / 결제 / 회원가입 등) 권장. 5 주차 본인 도메인은 STAGE 4 의 AOP vs Event 비교 자리에서만 활용.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 2. 클래스 구조 매핑
━━━━━━━━━━━━━━━━━━━━━━━━━━

도메인별 추천 클래스 구조. **이벤트 record + Publisher Service + 리스너 클래스들** 3 종 세트.

| 도메인 | 이벤트 record | Publisher | 리스너 (phase) |
|---|---|---|---|
| 1 주문 | `OrderPlacedEvent` | `OrderService.placeOrder()` | InventoryListener (BEFORE) / NotificationListener (AFTER_COMMIT) / StatsListener (AFTER_COMMIT @Async) / SlackListener (AFTER_COMMIT @Async) |
| 2 결제 | `PaymentCompletedEvent` | `PaymentService.complete()` | ReceiptListener (BEFORE_COMMIT) / PointListener (AFTER_COMMIT) / CompensationListener (AFTER_ROLLBACK) |
| 3 회원가입 | `UserRegisteredEvent` | `SignupService.register()` | WelcomeMailListener / CouponListener / StatsListener — 모두 AFTER_COMMIT + @Async |
| 4 송금 | `TransferCompletedEvent` | `TransferService.transfer()` | AuditListener (AFTER_COMMIT) / NotificationListener (AFTER_COMMIT) / ExternalSyncListener (AFTER_COMMIT) |
| 5 파일업로드 | `FileUploadedEvent` | `FileUploadService.upload()` | ThumbnailListener / SearchIndexListener / NotifyListener — 모두 @Async |
| 6 게시글 | `PostWrittenEvent` | `PostService.write()` | FollowerNotifyListener / IndexListener / StatsListener |
| 7 댓글 | `CommentWrittenEvent` | `CommentService.write()` | AuthorNotifyListener / MentionListener / StatsListener |
| 8 로그인 | `LoggedInEvent` | `LoginService.login()` | AuditListener (`fallbackExecution=true`) / StatsListener |
| 9 좋아요 | `LikedEvent` | `LikeService.like()` | CounterListener / AuthorNotifyListener |
| 10 재고 | `InventoryChangedEvent` | `InventoryService.decrement()` | StockShortageListener / ExternalSyncListener / CompensationListener (ROLLBACK) |
| 11 상품 | `ProductRegisteredEvent` | `ProductService.register()` | IndexListener / CacheInvalidateListener / NotifyListener |
| 12 회원탈퇴 | `UserWithdrewEvent` | `WithdrawalService.withdraw()` | DataCleanupListener (BEFORE_COMMIT) / ExternalNotifyListener (AFTER_COMMIT) / RecoveryListener (AFTER_ROLLBACK) / CleanupLogListener (AFTER_COMPLETION) |

## 공통 — STAGE 1 손 작성 (모두 동일)

`ApplicationEventPublisher` + `@EventListener` 가장 작은 단위 손으로 짜본다. Spring AOP 안 쓰고 — `@SpringBootApplication` + 3 개 Bean (Publisher / Event / Listener) 만으로:

```java
// (1) 이벤트 — record 로 충분 (Spring 4.2+)
public record HelloEvent(String message) {}

// (2) Publisher — ApplicationEventPublisher 주입
@Service
public class HelloService {
    private final ApplicationEventPublisher publisher;
    public HelloService(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }
    public void sayHello(String name) {
        System.out.println("[publisher] sayHello — " + name);
        publisher.publishEvent(new HelloEvent("hello " + name));
        System.out.println("[publisher] return");
    }
}

// (3) Listener — @EventListener 메서드 1 개
@Component
public class HelloListener {
    @EventListener
    public void onHello(HelloEvent event) {
        System.out.println("[listener] received — " + event.message());
    }
}

// (4) main
@SpringBootApplication
public class Stage1HelloEvent {
    public static void main(String[] args) {
        var ctx = SpringApplication.run(Stage1HelloEvent.class, args);
        ctx.getBean(HelloService.class).sayHello("world");
    }
}
```

**예상 출력** (동기):
```
[publisher] sayHello — world
[listener] received — hello world
[publisher] return                     ← 리스너 끝난 후에 publisher 가 반환
```

> 핵심: `publishEvent` 가 **같은 스레드에서 리스너 메서드를 직접 호출**한다. AOP 가 자동으로 가로채는 것과 달리, publisher 코드에 명시된 한 줄이 trigger. STAGE 1 에서 손맛 본 후 STAGE 2 부터 트랜잭션 결합.

## measurements.md 형식 (1, 2, 3, 4, 5 주차와 일관)

자동 누적 형식 그대로:
```
- [06-XX 14:00] s1 · HelloEvent 발행 → 동기 리스너 호출 순서 println 확인
- [06-XX 14:30] s1 · 리스너 2 개 + @Order — order=1 이 먼저
- [06-XX 14:45] s1 · 한 리스너에서 예외 던지면 다음 리스너 호출 안 됨 확인
- [06-XX 22:00] s2 · @EventListener 만 → commit 전 실행 시연 (롤백 후 이벤트 이미 처리됨)
- [06-XX 22:30] s2 · @TransactionalEventListener(AFTER_COMMIT) — 롤백 시 리스너 호출 X 확인
- [06-XX 22:45] s2 · BEFORE_COMMIT / AFTER_ROLLBACK / AFTER_COMPLETION 각 phase 출력 매트릭스
- [06-XX 23:00] s2 · 트랜잭션 밖 publishEvent → 무시. fallbackExecution=true 시 즉시 실행 확인
- [06-XX 22:00] s3 · 동기 리스너 5 개 × 200ms — publisher 1s 블록 측정
- [06-XX 22:15] s3 · @Async 적용 후 publisher 즉시 반환 (Xms)
- [06-XX 22:30] s3 · @Async self-invocation — 같은 클래스 호출 시 비동기 X 재현
- [06-XX 22:45] s3 · ThreadPoolTaskExecutor 설정 — core=4 / max=8 / queue=100 + 부하 시 행동
- [06-XX 23:00] s4 · 5 주차 @Audited → @EventListener 로 옮김 (코드 어떻게 바뀌었나)
- [06-XX 23:15] s4 · 본인 도메인 AOP + Event 결정 매트릭스 본인 답
```


━━━━━━━━━━━━━━━━━━━━━━━━━━
환경
━━━━━━━━━━━━━━━━━━━━━━━━━━
- Java 21
- **Spring Boot 3.x** (Spring 6.x) — `spring-boot-starter` 만으로 이벤트 가능 (별도 starter 없음)
- STAGE 2 의 트랜잭션 결합 — `spring-boot-starter-jdbc` + H2 인메모리 (또는 본인 5 주차 PostgreSQL 재사용)
- STAGE 3 의 비동기 — `@EnableAsync` 활성화 + `ThreadPoolTaskExecutor` 빈 등록
- 측정용: `System.nanoTime()`, `CountDownLatch`, `ConcurrentLinkedQueue`

## build.gradle 추가

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.0'
    id 'io.spring.dependency-management' version '1.1.4'
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter'

    // STAGE 2 트랜잭션 결합 — H2 인메모리 (또는 본인 5 주차 PostgreSQL 재사용)
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    runtimeOnly 'com.h2database:h2'

    // (선택) 5 주차 코드 그대로 가져온 학습자 — Lettuce / PostgreSQL
    // implementation 'io.lettuce:lettuce-core:6.3.0.RELEASE'
    // runtimeOnly 'org.postgresql:postgresql'
}

compileJava {
    options.compilerArgs += ['-parameters']     // 5 주차에서 익힘 — SpEL / 파라미터 바인딩 안전장치
}
```

> STAGE 1 (가장 작은 단위 publishEvent) 은 jdbc 없어도 됨. STAGE 2 부터 트랜잭션 결합으로 H2 또는 본인 DB.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STAGE 진행 가이드
━━━━━━━━━━━━━━━━━━━━━━━━━━

## 예상 학습 시간 (한 주 기준)

| 단계 | 시간 | 비고 |
|---|---|---|
| STAGE 1 (publishEvent + @EventListener + @Order + 예외 전파) | 1 ~ 2 시간 | **화요일까지 (필수)** |
| **STAGE 2 (`@TransactionalEventListener` 4 phase + fallbackExecution + 함정 2)** ★ | **2 ~ 3 시간** | **목요일까지 (필수)**. 6 주차 가장 중요한 학습 |
| STAGE 3 (`@Async` + 풀 함정 + 새 스레드 함정 + Virtual Thread) | 3 ~ 4 시간 | 5 주차 함정 회수 + 풀 / 가상스레드 |
| STAGE 4-1 / 4-2 (AOP vs Event 결정 매트릭스) | 1 ~ 2 시간 | 5 주차 자작 어노테이션 일부를 이벤트로 옮김 |
| **합계 (필수)** | **7 ~ 11 시간** | |
| STAGE 4-3 [여유] AOP + Event 통합 패턴 | 1 ~ 2 시간 | 4 / 5 / 6 주차 다 묶음. 난도 한 단계 위 |
| STAGE 5 [여유] (`@DomainEvents` — 7 주차 브릿지) | 30 ~ 60 분 | |

**배분**:
- 5 주차 (12 ~ 17 시간) 보다 분량 적지만 STAGE 3 의 함정 / 가상 스레드 추가로 약간 늘어남
- 직장인 (평일 저녁 1.5 시간 × 5 + 주말 4 시간) — 필수만 충분, 4-3 / 5 는 여유 시
- 학생 (주말 풀타임 1 일) — 필수 + 4-3 / 5 까지 가능
- 부담스러우면 **STAGE 2 + STAGE 3 의 self-invocation / 풀 함정이 면접 최강** — 시간 부족 시 STAGE 4 는 결정 매트릭스 표만

### [화 11:00 — Draft PR 마감 + 겪기 발표] — STAGE 1

> 6 주차는 **STAGE 1 (publishEvent + 리스너 호출 순서 관찰) 까지 화요일 분량**. STAGE 2 (`@TransactionalEventListener` 4 phase) 부터는 목요일까지.

#### ▸ STAGE 1 — `ApplicationEventPublisher` + `@EventListener` 손으로 (필수)

**목표**: Spring 이 자동으로 해주는 발행 / 분배를 코드 한 줄씩 손으로 짜본다. 가장 작은 단위부터.

##### 1-1. HelloEvent — 1 publisher + 1 listener

위 [공통 STAGE 1 손 작성](#공통--stage-1-손-작성-모두-동일) 의 4 클래스 그대로.

**관찰 포인트**:
- `[publisher] sayHello` → `[listener] received` → `[publisher] return` 순서 — **리스너가 publisher 의 같은 스레드에서 실행**
- `Thread.currentThread().getName()` 추가로 같은 스레드인지 확인
- 리스너 안에서 `Thread.sleep(1000)` 넣으면 publisher 도 1 초 블록 — 동기의 본질

##### 1-2. 리스너 여러 개 + `@Order`

같은 이벤트에 리스너 3 개:

```java
@Component
public class Listener1 {
    @EventListener
    @Order(1)
    public void on(HelloEvent e) { System.out.println("[L1 order=1] " + e.message()); }
}

@Component
public class Listener2 {
    @EventListener
    @Order(2)
    public void on(HelloEvent e) { System.out.println("[L2 order=2] " + e.message()); }
}

@Component
public class Listener3 {
    @EventListener
    @Order(3)
    public void on(HelloEvent e) { System.out.println("[L3 order=3] " + e.message()); }
}
```

**예상 출력**:
```
[publisher] publish
[L1 order=1] hello
[L2 order=2] hello
[L3 order=3] hello
[publisher] return
```

**관찰 포인트**:
- `@Order` 숫자 작은 게 먼저 (5 주차 `@Order` 와 같은 규칙)
- `@Order` 없으면 Spring 이 임의 순서 결정 — 불확정
- AOP 의 `@Order` 는 advice 안 / 밖 구조였는데, Event 의 `@Order` 는 단순 호출 순서

##### 1-3. 한 리스너에서 예외 던지면 다음 리스너는?

```java
@Component
public class FailingListener {
    @EventListener
    @Order(2)
    public void on(HelloEvent e) {
        System.out.println("[L2] received — throwing");
        throw new RuntimeException("일부러 실패");
    }
}
```

**관찰 포인트**:
- 동기 + 트랜잭션 무관 케이스 — **다음 리스너 (`L3`) 호출 안 됨** + 예외가 publisher 까지 전파
- publisher 의 `[publisher] return` 출력 안 됨 → 메서드 throw 로 종료
- STAGE 2 의 `@TransactionalEventListener` 는 동작이 달라짐 (각 phase 별로 격리)
- STAGE 3 의 `@Async` 도 동작이 또 다름 (예외 publisher 에 전파 X, AsyncUncaughtExceptionHandler 가 받음)

##### 1-4. `ApplicationListener<E>` 인터페이스 직접 구현

`@EventListener` 가 권장이지만, 옛 방식 (인터페이스 구현) 도 한 번 봐두면 메커니즘 이해.

```java
@Component
public class OldStyleListener implements ApplicationListener<HelloEvent> {
    @Override
    public void onApplicationEvent(HelloEvent event) {
        System.out.println("[OldStyle] " + event.message());
    }
}
```

**관찰 포인트**:
- `@EventListener` 와 동작 동일. 단 한 클래스에 한 이벤트만 받을 수 있음 (제네릭이 클래스 시그니처에)
- `@EventListener` 는 한 클래스에 여러 메서드 / 여러 이벤트 OK
- Spring 내부 — `@EventListener` 는 `ApplicationListenerMethodAdapter` 로 감싸서 `ApplicationListener` 로 변환 → 결국 같은 메커니즘

##### 1-5. payload-only 이벤트 (Spring 4.2+ 와일드카드)

```java
// ApplicationEvent 상속 없이 그냥 String / record / POJO
@Component
public class StringListener {
    @EventListener
    public void on(String message) {
        System.out.println("[String] " + message);
    }
}

// publisher 쪽
publisher.publishEvent("그냥 문자열");      // OK — 타입으로 매칭
publisher.publishEvent(new HelloEvent("payload-only record"));
```

**관찰 포인트**:
- Spring 4.2+ 부터 이벤트는 그냥 객체. `ApplicationEvent` 상속 의무 X
- 단 너무 일반적 타입 (`String`, `Long`) 으로 발행하면 다른 곳의 리스너가 우연히 매칭될 수 있음 — 도메인 이벤트는 **전용 record** 권장
- 내부적으로 Spring 이 `PayloadApplicationEvent<T>` 로 감싸서 발행

##### 1-6. STAGE 1 결과 정리

`measurements.md` 또는 별도 섹션에:
```
## STAGE 1 — publishEvent + 리스너 (직접 관찰)

리스너 호출 스레드: publisher 와 동일 (Thread[main])
@Order 순서: 숫자 작은 게 먼저 (1 → 2 → 3)
@Order 없을 때 순서: 임의 (실행마다 다를 수 있음)
리스너 예외 전파: 다음 리스너 호출 안 됨 + publisher 에 throw
ApplicationListener 인터페이스 vs @EventListener: 동작 동일
payload-only (String 직접 발행) 동작: 정상 매칭 + PayloadApplicationEvent<String> 으로 감싸짐
```


### [목 11:00 — Ready PR 전환 + 코드 발표] — STAGE 2 ~ STAGE 4

> STAGE 1 (발행 / 리스너 손맛) 은 화요일까지. 목요일까지는 4 phase (STAGE 2) → `@Async` 함정 (STAGE 3) → AOP 비교 (STAGE 4).

#### ▸ STAGE 2 — `@TransactionalEventListener` 4 phase (필수, **6 주차 가장 중요**)

##### 2-1. 5 주차 advice 안-밖 순서의 한계 재현 — `@EventListener` 만 쓰면 commit 전 실행 (★ 핵심 학습 단계)

**🔴 5 주차 학습자가 직접 본 그 흐름의 정답을 만드는 자리.** 한 번에 정답 짜지 말고 순진한 버전 (그냥 `@EventListener`) → 함정 발견 → `@TransactionalEventListener` 순서로.

**Step 1 — 순진한 버전 (그냥 `@EventListener`)**

```java
public record OrderPlacedEvent(Long orderId, BigDecimal amount) {}

@Service
public class OrderService {
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher publisher;
    public OrderService(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
        this.jdbc = jdbc;
        this.publisher = publisher;
    }

    @Transactional
    public void placeOrder(Long orderId, BigDecimal amount) {
        jdbc.update("INSERT INTO orders(id, amount) VALUES (?, ?)", orderId, amount);
        publisher.publishEvent(new OrderPlacedEvent(orderId, amount));
        // 만약 여기서 예외가 발생하면? — 트랜잭션 롤백되지만, 리스너는 이미 실행됨
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("음수 금액");
        }
    }
}

@Component
public class NotificationListener {
    @EventListener
    public void on(OrderPlacedEvent e) {
        System.out.println("[알림] 주문 발생 — " + e.orderId() + " / " + e.amount());
        // 실제로는 이메일 / 슬랙 / SMS — 한 번 전송하면 회수 불가
    }
}
```

**Step 2 — 일부러 깨뜨려서 함정 확인**

`placeOrder(1L, BigDecimal.valueOf(-100))` 호출 — 음수라서 INSERT 후 예외:

| 기대 | 실제 |
|---|---|
| 트랜잭션 롤백 → INSERT 취소 → 알림도 안 전송 | INSERT 는 롤백됨 ✓ / **하지만 알림 이미 전송됨** ✗ |

**출력**:
```
INSERT INTO orders ...
[알림] 주문 발생 — 1 / -100     ← 알림 이미 전송
IllegalArgumentException: 음수 금액
(rollback)
```

→ 사용자에게는 "주문 실패" 알림이 가버림. 5 주차 advice 안-밖 순서의 한계의 정확한 재현.

**Step 3 — `@TransactionalEventListener(AFTER_COMMIT)` 로 해결**

```java
@Component
public class NotificationListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderPlacedEvent e) {
        System.out.println("[알림] 주문 발생 — " + e.orderId() + " / " + e.amount());
    }
}
```

같은 케이스 다시 호출 — INSERT 후 예외:

**출력**:
```
INSERT INTO orders ...
IllegalArgumentException: 음수 금액
(rollback)
                            ← 알림 호출 X (commit 안 됐으므로)
```

정상 케이스 (`placeOrder(1L, BigDecimal.valueOf(100))`):
```
INSERT INTO orders ...
(commit)
[알림] 주문 발생 — 1 / 100   ← commit 후 호출
```

**관찰 포인트**:
- 5 주차 `@Order` advice 안-밖에서는 "Audit 가 TX 안쪽" 이라 commit 전 실행 — 구조적 한계
- 6 주차는 시간축 (`AFTER_COMMIT`) 로 분리 → 트랜잭션 결과에 따라 자동 취소
- `@EventListener` → `@TransactionalEventListener` 어노테이션 한 줄 변경뿐. 학습 비용 최소
- Spring 내부 — `@TransactionalEventListener` 는 `TransactionSynchronization` 콜백을 등록 (`TransactionSynchronizationManager.registerSynchronization`). 5 주차 STAGE 2-1 의 ThreadLocal `TX_CONN` 과 같은 메커니즘

> 핵심: 5 주차 advice 안-밖 순서의 한계를 정확히 푸는 게 6 주차의 본질. `@TransactionalEventListener` 가 없는 세상에서는 "commit 후 처리" 를 위해 메서드 끝에 `try/commit + finally/notify` 같은 직접 코드를 짜야 했음 (`AfterReturning` advice 로 약간 가능했지만 같은 트랜잭션 안). 6 주차 이후는 시간축 phase 로 분리.

##### 2-2. 4 phase 각각 직접 출력

한 이벤트에 4 phase 리스너를 모두 붙여서 출력 확인:

```java
@Component
public class AllPhaseListener {

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onBefore(OrderPlacedEvent e) {
        System.out.println("  [BEFORE_COMMIT] " + e.orderId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAfterCommit(OrderPlacedEvent e) {
        System.out.println("  [AFTER_COMMIT] " + e.orderId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void onAfterRollback(OrderPlacedEvent e) {
        System.out.println("  [AFTER_ROLLBACK] " + e.orderId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    public void onAfterCompletion(OrderPlacedEvent e) {
        System.out.println("  [AFTER_COMPLETION] " + e.orderId());
    }
}
```

**정상 commit 시 출력**:
```
INSERT INTO orders ...
[publisher] publishEvent
  [BEFORE_COMMIT] 1
(commit)
  [AFTER_COMMIT] 1
  [AFTER_COMPLETION] 1
```

**rollback 시 출력**:
```
INSERT INTO orders ...
[publisher] publishEvent
(rollback)
  [AFTER_ROLLBACK] 1
  [AFTER_COMPLETION] 1
```

**관찰 포인트**:
- `BEFORE_COMMIT` 은 트랜잭션 안 — 여기서 추가 INSERT 가능. 단 실패하면 본 트랜잭션도 같이 롤백
- `AFTER_COMMIT` / `AFTER_ROLLBACK` 은 트랜잭션 종료 후 — 새 트랜잭션 필요하면 `@Transactional(propagation = REQUIRES_NEW)` 명시
- `AFTER_COMPLETION` 은 commit / rollback 무관 항상 실행 — 정리 작업 (캐시 / 로깅)

##### 2-3. 4 phase 별 사용 케이스 매트릭스

| phase | 언제 쓰나 | 예시 |
|---|---|---|
| `BEFORE_COMMIT` | 같은 트랜잭션 안에서 마지막 처리 | 영수증 발급 (같은 DB), 마지막 검증 |
| `AFTER_COMMIT` (기본) | 트랜잭션 확정 후 외부 부수 효과 | 알림, 외부 API, 이메일, 통계 집계 |
| `AFTER_ROLLBACK` | 실패 시 보상 처리 | 실패 통보, 캐시 정리, 외부 시스템 복구 |
| `AFTER_COMPLETION` | 성공 / 실패 무관 정리 | 로깅, 리소스 정리, 메트릭 집계 |

**도메인별 선택 예** (`12 회원 탈퇴` 도메인):
```
WithdrawalService.withdraw(userId)
  ├─ [BEFORE_COMMIT] DataCleanupListener      — 본인 게시글 / 댓글 anonymize (같은 트랜잭션)
  ├─ [AFTER_COMMIT]  ExternalNotifyListener   — 결제 PG / CRM 시스템에 탈퇴 통보
  ├─ [AFTER_ROLLBACK] RecoveryListener         — 외부 시스템에 "탈퇴 시도 실패" 알림 (보상)
  └─ [AFTER_COMPLETION] CleanupLogListener     — 감사 로그 (성공 / 실패 무관)
```

##### 2-4. fallbackExecution — 트랜잭션 밖에서 publishEvent

```java
@Component
public class LoginAuditListener {
    // 트랜잭션 없는 메서드에서도 호출되어야 함 → fallbackExecution=true
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT,
                                fallbackExecution = true)
    public void on(LoggedInEvent e) {
        System.out.println("[감사] login — " + e.userId());
    }
}

@Service
public class LoginService {
    private final ApplicationEventPublisher publisher;
    public LoginService(ApplicationEventPublisher publisher) { this.publisher = publisher; }

    // ★ @Transactional 없음 — 인증 자체는 트랜잭션 필요 없는 경우
    public void login(Long userId) {
        // ... 인증 로직
        publisher.publishEvent(new LoggedInEvent(userId));
    }
}
```

**관찰 포인트**:
- 기본 (`fallbackExecution=false`) — 트랜잭션 없으면 **리스너 호출 안 됨** (조용히 무시). WARN 로그조차 안 나옴 → 함정
- `fallbackExecution=true` — 트랜잭션 없으면 즉시 실행 (그냥 `@EventListener` 처럼)
- 8 번 로그인 도메인 학습자 — 인증은 트랜잭션 없는데 감사는 필요 → 이 패턴 강제

##### 2-5. 같은 트랜잭션 안에서 추가 발행 (`BEFORE_COMMIT` 활용)

```java
@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
public void onBefore(OrderPlacedEvent e) {
    // 같은 트랜잭션 안 — 여기서 INSERT 하면 본 트랜잭션과 함께 commit
    jdbc.update("INSERT INTO order_history(order_id, action) VALUES (?, 'PLACED')", e.orderId());

    // ★ 만약 여기서 예외 던지면? — 본 트랜잭션도 롤백
}
```

**관찰 포인트**:
- BEFORE_COMMIT 에서 예외 → 본 트랜잭션 rollback → `AFTER_ROLLBACK` 리스너 호출됨 (`AFTER_COMMIT` 은 X)
- 같은 DB 안의 부수 INSERT 면 BEFORE_COMMIT 이 자연 (한 트랜잭션 안에서 같이 묶임)
- 외부 시스템 / 알림은 절대 BEFORE_COMMIT 에 두지 말 것 — 회수 불가 + 본 트랜잭션 commit 보장 X

> ⚠️ **AFTER_COMMIT 리스너에서 DB 쓰기 — 직접 실행으로 결과 확인**
>
> 단정하기 위험. `AFTER_COMMIT` 시점은 트랜잭션이 물리적으로 commit 됐지만 **아직 `afterCompletion` 전 → `TransactionSynchronizationManager` 의 동기화가 살아있음**. 이 상태에서 `JdbcTemplate.update()` 부르면:
> - `DataSourceUtils.getConnection()` 이 새 conn 을 autoCommit=true 로 가져올 수도 있고
> - **트랜잭션에 묶여있던 기존 `ConnectionHolder` 를 반환할 수도 있음** — 이 conn 은 이미 commit 끝 + autoCommit=false 상태. INSERT 후 `afterCompletion` 에서 release 되면서 **commit 없이 닫혀 유실** 가능
>
> 즉 JDBC 도 H2 버전 / 동기화 상태에 따라 JPA 와 같은 "조용한 no-op" 재현 가능. 본인 환경에서 Stage2_4 실행해서 카운트 직접 확인.
>
> **JPA + Hibernate** (7 주차 영역) — `EntityManager.persist()` 가 영속성 컨텍스트에 보관만. flush 는 트랜잭션 commit 시점인데 본 트랜잭션 이미 끝 → flush 안 됨.
>
> **결론 — 환경 무관하게 `@Transactional(propagation = Propagation.REQUIRES_NEW)` 명시.** 새 트랜잭션이 답. JDBC / JPA 모두 안전.

##### 2-6. STAGE 2 측정 매트릭스

| 케이스 | publisher 의 `@EventListener` | `@TransactionalEventListener(AFTER_COMMIT)` |
|---|---|---|
| 정상 commit | 리스너 실행됨 (commit 전) | 리스너 실행됨 (commit 후) |
| 예외 → rollback | **리스너 실행됨** (이미 실행됨) ✗ | 리스너 실행 안 됨 ✓ |
| 트랜잭션 밖 호출 | 즉시 실행 | **조용히 무시** (fallback=false 기본) |
| 트랜잭션 밖 + fallback=true | 즉시 실행 | 즉시 실행 |

> ⚠️ **AFTER_COMMIT + @Async = at-most-once (메시지 큐와의 경계)**
>
> Spring Event 는 **단일 JVM / 인메모리**. `AFTER_COMMIT` 으로 commit 보장은 되지만, **commit 직후 ~ 리스너 실행 전에 프로세스가 죽으면 부수 효과는 그냥 유실**. 재처리도 없음 (at-most-once).
>
> **보장이 필요한 부수 효과** (결제 통보 / 외부 PG 호출) — Transactional Outbox 패턴 + Kafka / RabbitMQ. DB 에 outbox 테이블로 같이 INSERT → 별도 Worker 가 읽어서 MQ 발행 → at-least-once.
>
> 6 주차는 단일 JVM 학습. "Spring Event 의 신뢰성 천장" 을 인지하는 게 면접 답변 차별점 (학습 범위는 안 넘되 경계 설명).


#### ▸ STAGE 3 — `@Async` 비동기 (필수)

##### 3-1. 동기의 한계 — 리스너 느리면 publisher 도 블록

```java
@Component
public class SlowListener {
    @EventListener
    public void on(OrderPlacedEvent e) throws InterruptedException {
        System.out.println("[slow] start — " + Thread.currentThread().getName());
        Thread.sleep(500);
        System.out.println("[slow] end");
    }
}

// publisher 시간 측정
long t1 = System.nanoTime();
orderService.placeOrder(1L, BigDecimal.valueOf(100));
long elapsedMs = (System.nanoTime() - t1) / 1_000_000;
System.out.println("publisher 총 시간: " + elapsedMs + "ms");
```

**예상 출력**:
```
[publisher] publish
[slow] start — Thread[main]
[slow] end
[publisher] return
publisher 총 시간: 510ms       ← 리스너 시간이 그대로 publisher 시간에 더해짐
```

##### 3-2. `@EnableAsync` + `@Async` → 별 스레드

```java
@SpringBootApplication
@EnableAsync
public class Stage3Application { /* ... */ }

@Component
public class SlowListener {
    @Async                                                  // ← 한 줄 추가
    @EventListener
    public void on(OrderPlacedEvent e) throws InterruptedException {
        System.out.println("[slow] start — " + Thread.currentThread().getName());
        Thread.sleep(500);
        System.out.println("[slow] end");
    }
}
```

**예상 출력**:
```
[publisher] publish
[publisher] return
publisher 총 시간: 5ms          ← 즉시 반환
[slow] start — Thread[task-1]   ← 별 스레드에서 늦게
[slow] end
```

**관찰 포인트**:
- `@Async` + `@EventListener` 같이 붙일 때는 `@Async` 가 메서드 자체를 비동기로 / `@EventListener` 가 이벤트 매칭으로
- `Thread.currentThread().getName()` 으로 다른 스레드 확인
- 리스너 반환값은 publisher 에 못 돌아옴 (Future 없으면)

##### 3-3. self-invocation 함정 (5 주차 회수)

```java
@Service
public class OrderService {
    private final ApplicationEventPublisher publisher;
    public OrderService(ApplicationEventPublisher publisher) { this.publisher = publisher; }

    public void placeOrder(...) {
        ...
        publisher.publishEvent(new OrderPlacedEvent(...));
        this.sendNotification(orderId);       // ← this 호출 → @Async 무시
    }

    @Async
    public void sendNotification(Long orderId) {
        System.out.println("Thread: " + Thread.currentThread().getName());
        // 같은 클래스 안에서 호출되면 — 프록시 우회 → 비동기 X
    }
}
```

**관찰 포인트**:
- `Thread[main]` 출력 — 비동기로 안 됨
- 5 주차 `@Transactional` self-invocation 과 정확히 같은 메커니즘 — `this` 는 원본 객체, 프록시 우회
- 해결: (a) 자기 자신 주입 / (b) 클래스 분리 / (c) `ApplicationEventPublisher` 로 이벤트 발행 → 다른 리스너 클래스가 받음 (**가장 6 주차스러운 해법**)
- → 6 주차의 진가: self-invocation 함정을 우회하는 자연스러운 패턴 자체가 "이벤트 발행"

##### 3-4. `ThreadPoolTaskExecutor` 직접 설정 + Spring Boot 기본의 함정

```java
@Configuration
public class AsyncConfig {
    @Bean(name = "applicationTaskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("event-");
        executor.setRejectedExecutionHandler(
            new ThreadPoolExecutor.CallerRunsPolicy());      // 큐 가득 차면 호출 스레드가 직접 실행
        executor.initialize();
        return executor;
    }
}
```

> ⚠️ **Spring Boot 자동 executor 의 "max=무제한" 함정** (면접 단골)
>
> Spring Boot 2.1+ 자동 등록 `applicationTaskExecutor` 기본값:
> - `spring.task.execution.pool.core-size = 8`
> - `spring.task.execution.pool.queue-capacity = Integer.MAX_VALUE` ← **무제한 큐**
> - `spring.task.execution.pool.max-size = Integer.MAX_VALUE`
>
> 문자상 max 가 무제한이지만 **실제로는 풀이 core (8) 를 절대 넘지 않는다.** `ThreadPoolExecutor` 동작 규칙 — "core 다 참 → 큐에 쌓기 → **큐도 다 차야** max 까지 증설" 인데, 큐가 무제한이라 영원히 안 참 → max 도달 불가능. 사실상 **8 개 고정 + 무한 큐** 가 정답.
>
> → 위 직접 설정 (core=4 / max=8 / **queue=100**) 이 의미 있는 이유: queue=100 으로 막아두면 진짜 부하 시 max=8 까지 증설이 실제로 일어남.

**ThreadPoolExecutor 의 동작 규칙** (정확히):
1. 들어온 작업 < core → 새 스레드 (core 채울 때까지)
2. core 다 참 → 큐에 넣기
3. **큐도 다 참 → max 까지 새 스레드**
4. max 도 다 참 → `RejectedExecutionHandler` 정책 (`AbortPolicy` / `CallerRunsPolicy` / `DiscardPolicy` 등)

> 📌 **멀티캐스터 레벨 비동기 vs `@Async` 리스너 — 둘은 다른 메커니즘**
>
> - **멀티캐스터 전역** (`SimpleApplicationEventMulticaster.setTaskExecutor`) — **모든** 리스너가 비동기. 거친 단위
> - **`@Async`** — **개별** 리스너 비동기. 권장
>
> 멀티캐스터 전역 비동기를 켜면 `@TransactionalEventListener` 의 phase 콜백이 트랜잭션 동기화 (`TransactionSynchronizationManager`, ThreadLocal 기반) 와 어긋날 수 있음 (publishEvent 가 별 스레드라 ThreadLocal 못 봄). **per-listener `@Async` 가 정답**. 5 주차 `TX_CONN` 의 ThreadLocal 논의와 정확히 같은 자리.

##### 3-5. `@Async` 예외 — 어디로 가나

```java
@Async
@EventListener
public void failing(OrderPlacedEvent e) {
    throw new RuntimeException("리스너 안 예외");
}
```

**관찰 포인트**:
- 반환 타입이 `void` — 예외가 **그냥 사라짐**. publisher 에 전파 X. 로그조차 안 나올 수 있음 → 함정
- 반환 타입이 `Future<T>` — `future.get()` 호출 시 `ExecutionException` 으로 받음
- `AsyncUncaughtExceptionHandler` 빈 등록하면 void 메서드의 예외도 잡을 수 있음:

```java
@Configuration
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            System.err.println("[ASYNC ERROR] method=" + method.getName()
                + " ex=" + ex.getMessage());
        };
    }
}
```

> 📌 **`@Bean` executor + `AsyncConfigurer` 둘 다 쓸 때 우선순위**
>
> - `AsyncConfigurer.getAsyncExecutor()` 오버라이드 → **이게 우선**
> - 오버라이드 안 함 → Spring 기본 해석: 유일한 `TaskExecutor` 빈 → 없으면 `taskExecutor` 이름 → 없으면 `SimpleAsyncTaskExecutor` (요청마다 새 스레드, 위험)
>
> **권장 통일** — 둘 중 하나로:
> - (A) `AsyncConfigurer` 에서 executor + 예외 핸들러 같이 잡기
> - (B) `@Bean(name="applicationTaskExecutor")` + 별도 `AsyncConfigurer` (executor 는 오버라이드 X, 핸들러만)

##### 3-6. `@Async` + `@TransactionalEventListener(AFTER_COMMIT)` — 새 스레드 함정

`AFTER_COMMIT` + `@Async` 조합은 **완전히 새 스레드**에서 실행. 즉:

- `TransactionSynchronizationManager` (5 주차 ThreadLocal 회수) — **날아감**
- 영속성 컨텍스트 (7 주차 JPA) — 날아감 → Lazy 로딩하면 `LazyInitializationException`
- DB 연결 / 트랜잭션 — 없음

**문제 패턴**:
```java
@Async
@TransactionalEventListener(phase = AFTER_COMMIT)
public void on(OrderPlacedEvent e) {
    Order order = orderRepo.findById(e.orderId());   // ← 새 트랜잭션 없으면 위험
    order.getItems().size();                          // ← JPA 라면 LazyInit 폭발
}
```

**해결**:
```java
@Async
@TransactionalEventListener(phase = AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)   // ★ 새 트랜잭션
public void on(OrderPlacedEvent e) { /* ... */ }
```

→ 7 주차 JPA 학습 시 가장 자주 밟는 함정. 6 주차에서 미리 한 번 짚기.

##### 3-7. Virtual Thread (Java 21 + Spring Boot 3.2) — 풀 튜닝의 종말?

Java 21 / Boot 3.2 환경이라면 `application.properties` 한 줄로 가상 스레드 사용 가능:

```properties
spring.threads.virtual.enabled=true
```

켜면:
- `applicationTaskExecutor` 가 **가상 스레드 기반** `SimpleAsyncTaskExecutor` 로 바뀜 (Boot 가 알아서)
- I/O 바운드 리스너 (HTTP / DB / 외부 API) 는 풀 사이즈 튜닝 (core/max/queue) 고민 자체가 사라짐 — 가상 스레드는 거의 무제한 생성 가능, 블로킹은 캐리어 스레드 점유 안 함
- CPU 바운드는 여전히 ForkJoinPool — 가상 스레드 만능 아님

**관찰 포인트**:
- **검증은 `Thread.currentThread().isVirtual()`** — Boot 3.2 의 `SimpleAsyncTaskExecutor` 는 가상 스레드 모드에서도 `setThreadNamePrefix("task-")` 를 유지함. 스레드명만 보면 `task-1`, `task-2` 로 platform thread 와 구분 안 됨 → 이름 의존 X, `isVirtual()` boolean 으로 확인
- 명시 `@Bean("applicationTaskExecutor") ThreadPoolTaskExecutor` 등록한 stage (예: Stage3_2) 에서는 자동 설정이 덮어써져서 → `isVirtual()` = false. 가상 스레드 안 켜짐
- self-invocation 함정은 **가상 스레드와 무관하게 그대로** — 프록시 메커니즘 이슈. 비동기 도구가 바뀐다고 사라지지 않음
- Boot 3.2 + Java 21 면접에서 가장 최신 화두. "풀 튜닝 vs 가상 스레드" 결정 기준 한 줄 답할 수 있어야

##### 3-8. STAGE 3 측정 매트릭스

| 케이스 | publisher 시간 | 리스너 스레드 |
|---|---|---|
| 동기 (`@EventListener` 만) | 500ms (리스너 시간 그대로) | main |
| 비동기 (`@Async`) | 5ms | task-N |
| self-invocation (`this.asyncMethod()`) | 500ms (비동기 X) | main |
| `ApplicationEventPublisher` 우회 (별 클래스) | 5ms | task-N |
| `@Async` void 메서드 예외 | publisher 에 전파 X | (조용히 사라짐 또는 `AsyncUncaughtExceptionHandler`) |


#### ▸ STAGE 4 — AOP vs Event 통합 (필수)

##### 4-1. 5 주차 `@Audited` (AOP) → 6 주차 `@EventListener` 로 옮기기

**5 주차 코드** (AOP):
```java
@Service
public class OrderService {
    @Audited(action = "PLACE_ORDER")
    @Transactional
    public Long placeOrder(Long userId, List<Long> items) { /* ... */ }
}

@Aspect @Component @Order(2)
public class AuditAspect {
    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        // ... commit 전 (TX 안쪽) 에 실행됨
    }
}
```

**6 주차 코드** (Event):
```java
@Service
public class OrderService {
    private final ApplicationEventPublisher publisher;
    public OrderService(ApplicationEventPublisher publisher) { this.publisher = publisher; }

    @Transactional
    public Long placeOrder(Long userId, List<Long> items) {
        Long orderId = /* ... */;
        publisher.publishEvent(new OrderPlacedEvent(orderId, userId, items));    // ← 한 줄
        return orderId;
    }
}

@Component
public class AuditListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)           // ← commit 후
    public void on(OrderPlacedEvent e) {
        System.out.println("[감사] PLACE_ORDER — user=" + e.userId()
            + " items=" + e.items());
    }
}
```

**무엇이 바뀌었나**:
- `@Audited` 어노테이션 + Aspect 클래스 → 사라짐
- 메서드 끝에 `publishEvent` 한 줄 추가됨
- 감사 로직은 별 클래스 (`AuditListener`) 로 이동
- **commit 후로 시점이 바뀜** → 트랜잭션 롤백 시 감사 로그 안 남음 (5 주차 한계 해소)

##### 4-2. 결정 매트릭스 — 어느 쪽을 언제 쓰나

| 축 | AOP (5 주차) | Event (6 주차) |
|---|---|---|
| 트리거 방식 | 암묵적 (어노테이션) | 명시적 (`publishEvent`) |
| publisher 가 리스너 / advice 를 알아야 하나 | 모름 (분리) | 모름 (분리) |
| 코드 가시성 | 메서드 보기엔 안 보임 (Aspect 분리) | publisher 코드에 한 줄 명시됨 |
| 적용 범위 | 한 클래스 / 같은 패키지 횡단 관심사 | 모듈 간 / 도메인 간 부수 효과 전파 |
| 트랜잭션 시점 제어 | advice 안-밖 구조 — 안 / 밖 만 | 시간축 4 phase — 자유 |
| 비동기 | `@Async` 가능 (같은 함정) | `@Async` 가능 (같은 함정) |
| **언제 AOP** | 로깅 / 측정 / 권한 / 캐싱 / 트랜잭션 — 모든 메서드에 일관 끼움 | |
| **언제 Event** | | 알림 / 외부 API / 통계 / 다른 모듈 호출 — 다른 책임으로 분기 |
| **회색지대 (감사 로그)** | 같은 DB 안에 commit 같이 묶기 = AOP | 외부 시스템 / 회수 불가 처리 = Event |
| 면접 단골 답변 | "메서드 호출 가로채기" | "한 사건이 여러 모듈로 퍼지기" |

##### 4-3. [여유 시] 한 도메인에 AOP + Event 함께

> ⏰ **언제 하나**: 4-1 / 4-2 까지가 STAGE 4 의 필수. 4-3 은 4 주차 결제 + 5 주차 분산락 + 6 주차 이벤트를 한 도메인에 모두 묶는 통합 패턴이라 난도가 한 단계 더. **여유 있는 학습자만**.

실제 프로젝트는 둘 다 씀. 예: 4 주차 결제 + 5 주차 분산락 AOP + 6 주차 이벤트:

```java
@Service
public class PaymentService {
    private final ApplicationEventPublisher publisher;
    public PaymentService(ApplicationEventPublisher publisher) { this.publisher = publisher; }

    @DistributedLock(key = "user:#{userId}")         // 5 주차 — 동시 결제 막기
    @Transactional                                    // 트랜잭션
    @Audited(action = "PAYMENT")                      // 5 주차 — 같은 DB 감사 (commit 전 OK)
    public void pay(Long userId, BigDecimal amount) {
        // ... 결제 로직
        publisher.publishEvent(                      // 6 주차 — 외부 부수 효과
            new PaymentCompletedEvent(userId, amount));
    }
}

@Component
public class PaymentEventListeners {
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onReceipt(PaymentCompletedEvent e) { /* 영수증 이메일 */ }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onPoint(PaymentCompletedEvent e) { /* 포인트 적립 */ }

    @TransactionalEventListener(phase = AFTER_ROLLBACK)
    public void onRollback(PaymentCompletedEvent e) { /* 외부 PG 보상 */ }
}
```

**관찰 포인트**:
- AOP (분산락 / 트랜잭션 / 감사) 는 메서드 진입 / 종료 (advice 안-밖)
- Event (영수증 / 포인트 / 보상) 는 commit 결과 (시간축)
- 같이 쓰면 깔끔 — advice 안-밖 + 시간축이 직교 (orthogonal)


━━━━━━━━━━━━━━━━━━━━━━━━━━
─── 여기 아래는 선택 (시간 여유 시) ───
━━━━━━━━━━━━━━━━━━━━━━━━━━

### [선택] ▸ STAGE 5 — `@DomainEvents` 7 주차 브릿지

> ⏰ **언제 하나**: Ready PR (목 11:00) 이후 **여유 시에만**. STAGE 1 ~ 4 가 우선. 늦어도 **7 주차 시작 전 (다음 목)** 까지 안 해도 됨.

##### 5-1. JPA Entity 가 publisher 없이 이벤트 발행

Spring Data JPA 의 `@DomainEvents` (Aggregate Root) — Entity 안에서 `publishEvent` 없이 이벤트 발행:

```java
@Entity
public class Order extends AbstractAggregateRoot<Order> {
    @Id private Long id;
    private BigDecimal amount;

    public void place() {
        // ... 도메인 로직
        registerEvent(new OrderPlacedEvent(this.id, this.amount));   // publisher 없이
    }
}

// Repository.save() 호출 시 자동으로 registerEvent 한 이벤트들 발행
orderRepo.save(order);
```

**관찰 포인트**:
- `AbstractAggregateRoot` 의 `@DomainEvents` 메서드가 Repository.save() 호출 시 자동 발행
- publisher 주입 불필요 → 도메인 로직이 Spring 의존성 없이 순수
- 7 주차 JPA 의 `EntityManager` / 영속성 컨텍스트도 또 다른 암묵적 메커니즘 (AOP / Event 와 다른 결)


━━━━━━━━━━━━━━━━━━━━━━━━━━
금지 키워드 — STAGE 1 ~ 2 동안만 (검색 금지)
━━━━━━━━━━━━━━━━━━━━━━━━━━
- Kafka / RabbitMQ / SQS — 분산 메시지 큐. Spring Event 와 결 비슷하나 본질 다름 (네트워크 / 영속성). 학습 범위 밖
- Spring Cloud Stream / Spring Integration — 메시지 채널 추상화. 범위 밖
- CQRS / Event Sourcing — 도메인 패턴. 6 주차는 메커니즘 학습만, 패턴은 별개
- ApplicationEventPublisherAware — `ApplicationEventPublisher` 가 빈 주입으로 더 권장
- `@DomainEvents` 의 내부 동작 (`AbstractAggregateRoot`) — STAGE 5 에서만 살짝
- Reactive Spring (`Mono` / `Flux`) — 비동기의 다른 결. 범위 밖


━━━━━━━━━━━━━━━━━━━━━━━━━━
학습 기록 (자유)
━━━━━━━━━━━━━━━━━━━━━━━━━━

### 5 주차 회상 — 6 주차로 이어지는 지점

| 5 주차에서 본 것 | 6 주차에서 확장 |
|---|---|
| `@Order(1) TX + @Order(2) Audit` advice 안-밖에서 Audit 가 commit 전 실행 | `@TransactionalEventListener(AFTER_COMMIT)` 로 시간축 분리 |
| `@Transactional` self-invocation 함정 (this = 원본) | `@Async` 도 같은 함정. 해결책으로 publishEvent 자체가 자연스러운 우회 |
| `TransactionSynchronizationManager` (ThreadLocal) — Aspect 시작 conn 을 Repository 가 공유 | `@TransactionalEventListener` 가 같은 객체 (`TransactionSynchronization`) 에 콜백 등록 — 메커니즘 동일 |
| AOP = 메서드 호출 시 자동 advice (암묵적) | Event = 메서드 안 `publishEvent` 한 줄 (명시적) |
| Pointcut 표현식 + `@annotation` 으로 매칭 | 이벤트 타입으로 매칭. `@EventListener(condition = "...")` SpEL 도 가능 |

### 6 주차 참고 질문 (답하고 싶은 만큼만)
- 5 주차 advice 안-밖 순서의 한계가 6 주차에 어떻게 풀리는가
- `publisher.publishEvent(event)` 한 줄이 컨테이너 내부에서 일어나는 일 3 단계
- `@TransactionalEventListener` 4 phase 각각 언제 쓰는가 + 본인 도메인 예 1 개씩
- 트랜잭션 밖 `publishEvent` + `@TransactionalEventListener` 기본 동작 + `fallbackExecution=true` 효과
- `@Async` self-invocation 이 `@Transactional` 함정과 같은 메커니즘인 이유
- `ThreadPoolTaskExecutor` 기본 (`SimpleAsyncTaskExecutor`) 이 위험한 이유
- AOP vs Event 결정 매트릭스 — 본인 도메인 메서드 1 개로 결정 근거
- `ApplicationListener<E>` 인터페이스 직접 구현 vs `@EventListener` — 무엇이 다른가
- payload-only 이벤트 (Spring 4.2+) 와 일반 이벤트 차이 — 내부적으로 어떻게 처리되나
- `@EventListener(condition = "#event.amount > 1000")` SpEL 조건 — Spring 이 어떻게 평가하나

### 면접 단골 + 본인 답
- **"`@TransactionalEventListener` 가 푸는 문제"** — 트랜잭션 commit 후에만 외부 부수 효과 실행 (롤백 시 자동 취소)
- **"4 phase 각각 언제 쓰는가"** — BEFORE_COMMIT (같은 트랜잭션 마지막 처리) / AFTER_COMMIT (외부 알림) / AFTER_ROLLBACK (보상) / AFTER_COMPLETION (정리)
- **"AOP vs Event 차이"** — AOP = 암묵적 가로채기 (메서드 호출 시 자동) / Event = 명시적 발행 (publisher 코드에 한 줄)
- **"`@Async` 의 self-invocation"** — `@Transactional` 과 정확히 같은 프록시 메커니즘. `this` 는 원본 객체
- **"동기 vs 비동기 이벤트"** — 기본 동기 (같은 스레드 블록). `@Async` 로 비동기 (`ThreadPoolTaskExecutor`)
- **"`@Async` 예외가 사라지는 이유 + 해결"** — void 메서드는 호출자에 전파 X. `AsyncUncaughtExceptionHandler` 등록 또는 `Future<T>` 반환
- **"publisher 가 리스너를 모르는 이유 (publish-subscribe)"** — 결합도 낮춤. 리스너 추가 / 제거가 publisher 코드 영향 X
- **"트랜잭션 밖 publishEvent 의 `@TransactionalEventListener` 동작"** — 기본 무시 (조용히). fallbackExecution=true 면 즉시 실행
- **"5 주차 @Audited 를 6 주차로 옮기면 어떻게 바뀌나"** — 메서드의 어노테이션 제거 + `publishEvent` 한 줄 추가 + `@TransactionalEventListener(AFTER_COMMIT)` 리스너 클래스 분리. commit 시점 보장 추가
- **"5 주차 IoC / AOP / 6 주차 Event 의 관계"** — IoC = 객체 생성 / DI = 의존성 연결 / AOP = 메서드 호출 가로채기 / Event = 메서드 안 명시적 발행. 모두 컨테이너 안 협업
- **"Spring Boot 자동 `applicationTaskExecutor` 의 함정"** — 기본 core=8 / queue=Integer.MAX_VALUE / max=Integer.MAX_VALUE. **무한 큐 때문에 max 도달 불가** → 사실상 8 개 고정. 진짜 증설 원하면 queue 를 유한 값으로
- **"멀티캐스터 비동기 vs `@Async` 차이"** — 멀티캐스터 전역 = 모든 리스너 비동기 + phase 와 ThreadLocal 충돌 위험 / `@Async` = 개별 리스너. per-listener `@Async` 가 정답
- **"`AFTER_COMMIT` + `@Async` 의 신뢰성"** — at-most-once. commit 됐는데 리스너 실행 전 프로세스 죽으면 부수 효과 유실. 보장 필요하면 **Transactional Outbox + MQ (Kafka 등)**. Spring Event 는 단일 JVM 학습용
- **"`AFTER_COMMIT` 리스너에서 DB 쓰기가 조용히 안 되는 이유"** — 본 트랜잭션은 이미 commit / 정리됨. 리스너가 같은 트랜잭션에 참여만 시도 → flush 안 됨. `@Transactional(propagation = REQUIRES_NEW)` 명시 필수
- **"`@Async` + `AFTER_COMMIT` 에서 영속성 컨텍스트 / `LazyInitializationException`"** — 새 스레드라 영속성 컨텍스트 / TransactionSynchronizationManager 다 날아감. JPA 면 Lazy 로딩 폭발. REQUIRES_NEW 로 새 트랜잭션
- **"Java 21 + Spring Boot 3.2 가상 스레드"** — `spring.threads.virtual.enabled=true` 한 줄. I/O 바운드 리스너의 풀 사이즈 튜닝 고민 자체가 사라짐. **단 self-invocation 함정은 그대로** (프록시 메커니즘 이슈, 스레드 도구와 무관)

### 실무 확장 화두 (스터디 토론 / 면접 후속 질문)
- **`@TransactionalEventListener` 의 내부 메커니즘**: `TransactionSynchronizationManager.registerSynchronization(...)` 으로 콜백 등록 — 5 주차 `TX_CONN.set/get` 의 ThreadLocal 과 같은 메커니즘. Aspect 와 Event 가 결국 같은 자리에서 만남
- **`@Async` 의 스레드풀 선택**: 기본 `SimpleAsyncTaskExecutor` (요청마다 새 스레드, 위험) → `ThreadPoolTaskExecutor` 명시. `applicationTaskExecutor` 빈 이름이 표준
- **Spring Boot 자동 executor 의 "max=무제한" 함정**: 기본 core=8 / queue=Integer.MAX_VALUE / max=Integer.MAX_VALUE. `ThreadPoolExecutor` 동작 규칙상 큐가 무제한이면 max 도달 불가 → 사실상 8 개 고정. 진짜 증설 원하면 `spring.task.execution.pool.queue-capacity` 를 유한 값으로
- **멀티캐스터 비동기 vs `@Async`**: `SimpleApplicationEventMulticaster.setTaskExecutor` = 전역 / `@Async` = 개별. 전역은 `@TransactionalEventListener` 의 phase 콜백이 ThreadLocal 기반이라 충돌 → **per-listener `@Async` 가 정답**
- **`@Bean` executor + `AsyncConfigurer` 우선순위**: `AsyncConfigurer.getAsyncExecutor()` 오버라이드 → 이게 우선. 안 하면 유일한 `TaskExecutor` 빈 → 없으면 `taskExecutor` 이름 → 없으면 `SimpleAsyncTaskExecutor`. 두 방식 섞으면 혼란 → 통일
- **`@Async` + `AFTER_COMMIT` 의 새 스레드 함정**: 새 스레드라 `TransactionSynchronizationManager` / 영속성 컨텍스트 다 날아감. 리스너에서 DB 조회 / Lazy 로딩 시 `LazyInitializationException` 위험. `@Transactional(propagation = REQUIRES_NEW)` 명시
- **`AFTER_COMMIT` 의 at-most-once 한계**: Spring Event 는 단일 JVM / 인메모리. commit 직후 ~ 리스너 실행 전 프로세스 죽으면 부수 효과 유실. 보장 필요 시 **Transactional Outbox + Kafka / RabbitMQ** (at-least-once). 6 주차 범위 밖이지만 면접 답변 차별점
- **`AsyncUncaughtExceptionHandler`**: `@Async` void 메서드의 예외가 사라지는 함정. `AsyncConfigurer` 구현 + override
- **Java 21 + Boot 3.2 Virtual Thread**: `spring.threads.virtual.enabled=true` 한 줄. `applicationTaskExecutor` 가 가상 스레드 기반으로 바뀜. I/O 바운드 리스너의 풀 사이즈 튜닝 고민이 사라짐 (블로킹이 캐리어 스레드 점유 안 함). 단 CPU 바운드는 여전히 ForkJoinPool. self-invocation 함정은 가상 스레드와 무관 (프록시 이슈)
- **이벤트 발행 위치**: `publishEvent` 를 트랜잭션 안 (commit 전) 에 호출하더라도 `@TransactionalEventListener(AFTER_COMMIT)` 면 commit 후로 미뤄짐. publisher 의 호출 시점과 리스너의 실행 시점이 분리됨
- **`@EventListener(condition = "...")`**: SpEL 조건으로 필터링. 5 주차 자작 `@DistributedLock(key="#fromId")` 와 같은 SpEL 메커니즘
- **이벤트의 순서 보장**: 한 publisher 의 여러 publishEvent 호출은 등록 순서대로 분배. 하지만 `@Async` 비동기 리스너는 스레드풀 스케줄링에 따라 순서 깨질 수 있음
- **Spring Event vs Kafka / RabbitMQ**: Spring Event 는 같은 JVM 안. 분산 환경 / 영속성 / 재처리 필요 시 메시지 큐. 6 주차는 단일 JVM 학습
- **`@DomainEvents` (7 주차 브릿지)**: JPA Entity 가 publisher 없이 발행. Repository.save() 가 자동 발행. 도메인 객체가 Spring 의존성 없이 순수해짐
- **CQRS / Event Sourcing**: 이벤트를 단순 부수 효과 트리거가 아닌 **상태 그 자체** 로 사용. 6 주차 범위 밖이지만 같은 출발점

### AOP / Event 선택 매트릭스 (면접 답변 기준)

| 상황 | 선택 | 이유 |
|---|---|---|
| 로깅 / 측정 / 권한 / 캐싱 / 트랜잭션 | AOP | 모든 메서드에 일관 끼움. 횡단 관심사 |
| 알림 / 이메일 / 외부 API / 통계 | Event | 다른 모듈로 부수 효과 전파. commit 후 |
| 감사 로그 (같은 DB 안) | AOP 또는 Event (BEFORE_COMMIT) | 같은 트랜잭션이면 둘 다 OK. 단 commit 보장 필요면 Event |
| 감사 로그 (외부 시스템) | Event (AFTER_COMMIT) | 회수 불가 → commit 후 보장 필요 |
| 실패 시 보상 (외부 시스템 복구) | Event (AFTER_ROLLBACK) | AOP 의 `AfterThrowing` 는 같은 트랜잭션 안 |
| 분산락 (Redis SETNX / Lua) | AOP | 메서드 진입 / 종료 보장 + 같은 트랜잭션 안 |
| 한 메서드에 여러 부수 효과 (3 ~ 5 개) | Event | 리스너 분리 → 각각 책임 명확 |


━━━━━━━━━━━━━━━━━━━━━━━━━━
막힐 때
━━━━━━━━━━━━━━━━━━━━━━━━━━
1. 직접 시도 (`CLAUDE.md` 룰)
2. AI 에 물어보기 — 3 단계 힌트만 받음 (코드 직답 X)
3. 디스코드 `#질문` 채널 — 코드 + 본인 이벤트 record + 리스너 함께

**`@EventListener` 가 안 호출되는 경우 체크리스트**:
1. `@Component` 로 Bean 등록되었는지 (리스너 클래스)
2. 메서드가 `public` 인지
3. 이벤트 타입이 정확히 일치하는지 (제네릭 / 상속 주의)
4. `@TransactionalEventListener` 인데 트랜잭션 밖에서 publishEvent 호출했는지 → 기본 무시. `fallbackExecution=true` 추가
5. 트랜잭션은 있는데 rollback 됐는지 → `AFTER_COMMIT` 은 commit 안 됐으면 호출 X
6. `@Async` 가 self-invocation 으로 무시되고 있는지 → 클래스 분리 또는 이벤트 발행으로 우회

**`@Async` 가 비동기로 안 동작하는 경우**:
1. `@EnableAsync` 활성화 누락
2. self-invocation — 같은 클래스 안에서 `this.asyncMethod()` 호출
3. 메서드가 `public` 아님 / `final` / `private`
4. 호출자가 같은 빈 메서드를 호출 (5 주차 `@Transactional` 함정과 동일)

**`@Async` 예외가 사라짐**:
1. void 메서드 — `AsyncUncaughtExceptionHandler` 등록 또는 `Future<T>` 반환으로 변경
2. `AsyncConfigurer.getAsyncUncaughtExceptionHandler()` 구현

**`ApplicationEventPublisher` 주입이 안 됨**:
- Spring 6+ 는 컨테이너가 기본으로 등록. 단 일반 클래스는 `@Component` / `@Service` 인지 확인
- 생성자 주입 권장 (5 주차 학습)

**`AFTER_COMMIT` 리스너에서 DB 쓰기가 조용히 안 됨**:
- 본 트랜잭션은 이미 commit / 정리 → 새 트랜잭션 필요
- 리스너 메서드에 `@Transactional(propagation = Propagation.REQUIRES_NEW)` 명시
- 예외도 안 나고 저장도 안 되는 패턴 → 가장 디버깅 어려운 함정

**`@Async` 리스너에서 `LazyInitializationException` / NPE**:
- `AFTER_COMMIT` + `@Async` 는 완전히 새 스레드 → 영속성 컨텍스트 / `TransactionSynchronizationManager` 다 날아감
- 리스너 안에서 Entity 의 Lazy 컬렉션 접근 시 폭발
- 해결: (a) 리스너에 `@Transactional(REQUIRES_NEW)` 로 새 트랜잭션 / (b) 이벤트 payload 에 필요한 데이터 미리 포함 (record 풍부하게)

**가상 스레드 켰는데 동작이 이상함** (Java 21 + Boot 3.2):
- `spring.threads.virtual.enabled=true` 켜면 `applicationTaskExecutor` 가 가상 스레드 기반으로 바뀜
- 직접 `@Bean("applicationTaskExecutor") ThreadPoolTaskExecutor` 등록하면 자동 설정 덮어쓰므로 → 가상 스레드 안 켜짐
- 둘 다 원하면 `@Bean` 이름을 다르게 하고 `@Async("myExecutor")` 로 명시
