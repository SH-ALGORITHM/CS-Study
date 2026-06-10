# 측정 및 관찰 기록

- [06-08 04:26] STAGE 1-1 — publishEvent + @EventListener
  - **관찰 결과**: `[publisher] 시작 -> [listener] 실행 -> [publisher] 끝` 순서로 호출됨.
  - **결론**: `publishEvent`는 기본적으로 **동기(Synchronous)** 방식이며, 발행자와 리스너가 **동일한 스레드**(`restartedMain`)에서 실행됨을 확인.

- [06-08 04:41] STAGE 1-2 — 리스너 3 개 + @Order
  - **관찰 결과**: L1(order=1) -> L2(order=2) -> L3(order=3) 순서로 리스너가 실행된 후 발행자가 종료됨.
  - **결론**: 한 이벤트에 여러 리스너가 붙을 수 있으며, `@Order`를 통해 실행 순서를 명시적으로 제어할 수 있음(숫자가 작을수록 우선순위 높음).

- [06-08 04:55] STAGE 1-3 — 리스너 예외 전파 확인
  - **관찰 결과**: L1 실행 -> L2에서 예외 발생 -> **L3 실행 안 됨** -> 발행자가 예외를 Catch 함.
  - **결론**: 동기(Synchronous) 리스너의 경우, 중간 리스너에서 예외가 발생하면 후속 리스너는 호출되지 않으며 예외가 발행자에게 전파됨.

- [06-08 05:10] STAGE 1-4 — ApplicationListener 인터페이스 vs @EventListener
  - **관찰 결과**: 두 가지 방식(인터페이스 구현, 어노테이션 사용) 리스너가 모두 정상 호출됨.
  - **결론**: 스프링 이벤트는 과거 방식(`ApplicationListener` 인터페이스)과 현대 방식(`@EventListener`)을 모두 지원하며, 내부적으로는 동일한 메커니즘으로 동작함. 유연성이 높은 `@EventListener`가 권장됨.

- [06-08 05:25] STAGE 1-5 — payload-only 이벤트 (Spring 4.2+)
  - **관찰 결과**: `record` 뿐만 아니라 `String`과 같은 일반 객체(POJO)도 이벤트를 발행하고 구독할 수 있음.
  - **결론**: `ApplicationEvent`를 상속받지 않아도 어떤 객체든 이벤트로 사용 가능함. 내부적으로는 `PayloadApplicationEvent`로 감싸져 처리됨. 도메인 의미를 명확히 하기 위해 전용 `record` 사용이 권장됨.


  
- [06-08 04:49] STAGE 1-5 — payload-only 이벤트 (Spring 4.2+)
- [06-10 21:21] STAGE 2-1 — 트랜잭션 롤백 함정 시연
  - 최종 재고 (100에서 10 차감 시도 후 롤백): 100
- [06-10 21:22] STAGE 2-2 — @TransactionalEventListener(AFTER_COMMIT)
  - **(1) 정상 커밋 시**
  - 재고 확인: 90
  - **(2) 롤백 시**
  - 재고 확인 (롤백되어 90 유지되어야 함): 90
- [06-10 21:32] STAGE 2-1 — AFTER_COMMIT (재고 도메인)
  - **정상 커밋 상황**
  - 현재 재고: 80
  - **롤백 상황 (알림 로그가 찍히지 않아야 함)**
  - 현재 재고 (롤백되어 90 유지): 80
- [06-10 21:35] STAGE 2-2 — 4 Phase 관찰 (재고 도메인)
  - **정상 커밋 (amount=10)**
  - **롤백 상황 (amount=0)**
- [06-10 22:29] STAGE 2-3 — fallbackExecution (트랜잭션 밖에서 발행)
- [06-10 23:19] STAGE 2-4 — AFTER_COMMIT 리스너에서의 DB 쓰기 함정
  - **decrement(1, 10) 실행**
  - 최종 히스토리 카운트: 2
- [06-11 00:25] STAGE 3-1 — 동기 리스너의 블록킹 현상 (재고 도메인)
- [06-11 00:38] STAGE 3-2 — @Async 비동기 리스너 도입 (재고 도메인)
- [06-11 00:45] STAGE 3-3 — @Async Self-Invocation 함정 (재고 도메인)
  - **(1) 클래스 내부 @Async 호출 (this)**
  - **(2) 주입된 다른 빈의 @Async 호출**
  - **(3) publishEvent -> @Async @EventListener**
- [06-11 00:54] STAGE 3-4 — 비동기 void 예외 핸들링 (재고 도메인)
- [06-11 01:05] STAGE 3-5 — Virtual Thread 활용 (재고 도메인)
- [06-11 01:21] STAGE 4-1 — AOP Audit vs Event Audit
  - **(1) Old AOP — 정상 케이스**
  - **(2) Old AOP — 롤백 케이스 (AOP 로그는 남음)**
  - **(3) New Event — 정상 케이스**
  - **(4) New Event — 롤백 케이스 (이벤트 로그 안 남음)**
- [06-11 01:38] STAGE 4-2 — AOP + Event 혼합 전략 (재고 도메인)
  - **Case 1: 정상 처리 (amount=10)**
  - **Case 2: 롤백 처리 (amount=0)**
