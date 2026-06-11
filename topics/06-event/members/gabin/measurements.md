# 측정 기록

실행 시 자동 누적됩니다.
- [06-10 21:22] s1-1 · HelloEvent 발행 → @EventListener 동기 호출 / publisher와 listener 모두 main
- [06-10 21:38] s1-2 · 리스너 3개 호출 순서 확인 — @Order 1 → 2 → 3
- [06-10 22:17] s1-3 · 동기 L2 예외 → L3 호출 중단 / 예외가 publisher로 전파
- [06-10 22:33] s1-4 · @EventListener와 ApplicationListener 모두 호출 — 내부 메커니즘 동일
- [06-10 22:47] s1-5 · ApplicationEvent 상속 없이 record와 String payload 이벤트 수신

- [06-11 06:23] s2-1-trap · @EventListener는 commit 전 실행 — rollback 후 orders=1 / 알림은 이미 호출됨
- [06-11 06:46] s2-1 · @TransactionalEventListener(AFTER_COMMIT) — rollback 후 orders=1 / rollback 이벤트 호출 X
- [06-11 07:23] s2-2 · 정상=BEFORE_COMMIT→AFTER_COMMIT→AFTER_COMPLETION / rollback=AFTER_ROLLBACK→AFTER_COMPLETION
- [06-11 07:44] s2-3 · 트랜잭션 밖 발행 — fallback=false 무시 / fallback=true 즉시 실행
- [06-11 07:58] s2-4 · AFTER_COMMIT DB 쓰기 — history=2 / REQUIRES_NEW 이력 반영 확인
- 
- [06-11 08:24] s3-1 · 동기 리스너 3개 x 200ms — publisher=625ms / thread=main
- [06-11 08:31] s3-2 · @Async 리스너 3개 — publisher=13ms / listener threads=event-N / core=4 max=8 queue=100
- [06-11 08:45] s3-3 · this.@Async=self 호출로 main / 다른 Bean과 Event 리스너=비동기
- [06-11 08:59] s3-4 · @Async void 예외는 caller에 전파 X / AsyncUncaughtExceptionHandler가 수신
- [06-11 09:09] s3-5 · Virtual Thread 비동기 리스너 3개 — publisher=32ms / isVirtual=true
- 
- [06-11 09:25] s4-1 · AOP audit=commit 전·rollback도 기록 / Event audit=commit 후·rollback 미호출
- [06-11 09:43] s4-2 · AOP=메서드 진입·종료 / Event=commit 알림·rollback 보상 동시 적용
