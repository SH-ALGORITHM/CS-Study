# 6주차 예시 코드 — 주문 이벤트 (Spring Event + @TransactionalEventListener + @Async)

scenario.md 의 12 개 도메인과 **별개로** 만든 참고 코드입니다.
5 주차의 `@MyTransactional` / `@Audited` 가 메서드 호출을 가로채는 AOP 였다면, **이번엔 메서드 안에서 명시적으로 이벤트를 발행해서 commit 후로 부수 효과를 분리**.

> ⚠️ **베끼지 마시고 본인 도메인으로 변환하세요.** "이런 식으로 흘러가는구나" 감을 잡는 용도.

## 5 주차와 무엇이 같고 다른가

| | 5 주차 Audit / Transactional | 6 주차 Event |
|---|---|---|
| 풀려고 하는 문제 | 메서드 호출 가로채기 (공통 관심사) | 한 사건이 여러 모듈로 퍼지기 (commit 후 보장) |
| 도구 | `@Aspect` / `@Around` / `Pointcut` | `ApplicationEventPublisher` / `@EventListener` / `@TransactionalEventListener` / `@Async` |
| 트리거 방식 | 암묵적 (어노테이션) | 명시적 (`publishEvent` 한 줄) |
| 시점 제어 | 양파 — 안 / 밖만 | 시간축 — 4 phase 자유 |
| 5 주차 양파 한계 | Audit 가 TX 안쪽 → commit 전 발사 | AFTER_COMMIT 으로 commit 후만 |
| self-invocation 함정 | `@Transactional` / `@Async` 동일 | `publishEvent` 가 자연스러운 우회 |
| 면접 직결 | `@Transactional` 안 먹는 3 가지 | 4 phase 차이 / `@Async` 함정 / AOP vs Event |

핵심: 5 주차 STAGE 4 의 self-invocation 함정이 6 주차 STAGE 3 에서 정확히 재현되며, 6 주차의 정답 (publishEvent → 다른 리스너) 이 가장 자연스러운 우회.

## 폴더 구조

> 📌 **5 주차와 다른 점 — self-contained stage 구조**
>
> 5 주차 example 은 `domain/` 패키지에 공용 도메인 (`OrderService`, `Audited`, `AuditAspect` 등) 을 두고 각 stage 가 import 해서 썼다. 6 주차는 stage 마다 다른 이벤트 record 시그니처 / 다른 phase 리스너 / 다른 executor 를 시연하므로 공용으로 묶기 어렵다. 각 stage 파일에 이벤트 record + publisher + 리스너를 inner class 로 모아둠 → **한 파일만 열면 전체 흐름 파악 가능**. `domain/` 패키지 자체가 없는 이유.

```
example/
├── README.md                              # 지금 이 파일
├── build.gradle                           # Spring Boot 3.x + jdbc + aop + h2
├── src/main/
│   ├── java/
│   │   ├── infra/
│   │   │   └── MeasurementLog.java         # 측정 / 출력 헬퍼 (공용)
│   │   └── stage/                          # 각 stage = 자기완결 (이벤트 + publisher + 리스너 inner)
│   │       ├── s1/                             # STAGE 1: publishEvent 손맛
│   │       │   ├── Stage1_1_HelloEvent.java        # 가장 작은 단위
│   │       │   ├── Stage1_2_MultipleListeners.java # 리스너 3 개 + @Order
│   │       │   ├── Stage1_3_ListenerException.java # 리스너 예외 → 다음 X
│   │       │   ├── Stage1_4_OldStyleListener.java  # ApplicationListener 인터페이스 vs @EventListener
│   │       │   └── Stage1_5_PayloadOnly.java       # Spring 4.2+ payload-only
│   │       ├── s2/                             # STAGE 2: @TransactionalEventListener 4 phase
│   │       │   ├── Stage2_1_BeforeCommitTrap.java  # 그냥 @EventListener → commit 전 발사 (함정)
│   │       │   ├── Stage2_1_AfterCommit.java       # AFTER_COMMIT 해결
│   │       │   ├── Stage2_2_AllPhases.java         # 4 phase 한꺼번에
│   │       │   ├── Stage2_3_FallbackExecution.java # 트랜잭션 밖 + fallbackExecution
│   │       │   └── Stage2_4_AfterCommitDbWrite.java # AFTER_COMMIT DB 쓰기 — JDBC vs JPA 환경별
│   │       ├── s3/                             # STAGE 3: @Async + Virtual Thread
│   │       │   ├── Stage3_1_SyncSlow.java          # 동기 한계 (publisher 블록)
│   │       │   ├── Stage3_2_AsyncFast.java         # @Async + ThreadPoolTaskExecutor + Boot 자동 함정 주석
│   │       │   ├── Stage3_3_SelfInvocation.java    # @Async self-invocation (5주차 회수)
│   │       │   ├── Stage3_4_AsyncException.java    # void 예외 → AsyncUncaughtExceptionHandler
│   │       │   └── Stage3_5_VirtualThread.java     # Java 21 + Boot 3.2 가상 스레드
│   │       └── s4/                             # STAGE 4: AOP vs Event
│   │           ├── Stage4_1_AopToEvent.java        # 5주차 @Audited → 6주차 이벤트로
│   │           └── Stage4_2_AopPlusEvent.java      # AOP + Event 동시 사용
│   └── resources/
│       ├── application.properties              # H2 인메모리 DB
│       └── schema.sql                          # orders 테이블
```

## 실행 방법

```bash
cd topics/06-event/example

# STAGE 1-1 — publishEvent + @EventListener 가장 작은 단위
./gradlew run -PmainClass=stage.s1.Stage1_1_HelloEvent

# STAGE 1-2 — 리스너 3 개 + @Order
./gradlew run -PmainClass=stage.s1.Stage1_2_MultipleListeners

# STAGE 2-1 — @EventListener 만 → commit 전 발사 (함정 재현)
./gradlew run -PmainClass=stage.s2.Stage2_1_BeforeCommitTrap

# STAGE 2-1 — @TransactionalEventListener(AFTER_COMMIT) 해결
./gradlew run -PmainClass=stage.s2.Stage2_1_AfterCommit

# STAGE 2-2 — 4 phase 출력 순서
./gradlew run -PmainClass=stage.s2.Stage2_2_AllPhases

# STAGE 3-3 — @Async self-invocation (5주차 회수)
./gradlew run -PmainClass=stage.s3.Stage3_3_SelfInvocation

# STAGE 4-1 — 5주차 AOP → 6주차 이벤트
./gradlew run -PmainClass=stage.s4.Stage4_1_AopToEvent

# ... 나머지도 동일 패턴
```

## 핵심 학습 흐름

1. **STAGE 1** — publishEvent + @EventListener 가장 작은 단위 손맛 (리스너 호출 순서 / @Order / 예외 전파)
2. **STAGE 2** ★ — `@EventListener` 만 쓰면 commit 전 발사 → `@TransactionalEventListener(AFTER_COMMIT)` 로 해결. **6 주차 가장 중요한 학습**
3. **STAGE 3** — 동기 한계 → `@Async` + ThreadPoolTaskExecutor. self-invocation 함정 (5 주차 회수) → publishEvent 가 자연스러운 우회
4. **STAGE 4** — 5 주차 자작 `@Audited` (AOP) 를 6 주차 이벤트로 옮기는 자리. AOP + Event 동시 사용 패턴

> **STAGE 2 가 6 주차 가장 중요한 학습**. 5 주차 양파 한계 (Audit 가 commit 전 발사) 를 직접 재현 → `AFTER_COMMIT` 으로 해소까지 한 흐름으로.
