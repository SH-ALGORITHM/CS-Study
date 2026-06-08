# 측정 기록 (6주차 — Spring Event)

도메인: **결제(payment)**. `MeasurementLog.save(stage, note)` 로 각 STAGE 관찰을 자동 누적.
아래 `- [시각] sX-Y · ...` 항목은 코드 실행 시 자동 기록되고, 해석 메모는 직접 덧붙인다.

## STAGE 1 — publishEvent + @EventListener (직접 관찰)

`ApplicationEventPublisher` 가 자동으로 해주는 발행/분배를 가장 작은 단위부터 손으로 확인.

| 단계 | 한 줄 결론 |
|---|---|
| 1-1 | 리스너는 publisher 와 **같은 스레드에서 동기 호출** (`return` 이 리스너 뒤에 출력) |
| 1-2 | 호출 순서는 `@Order` 숫자가 결정 (작을수록 먼저, 선언/이름 무관) |
| 1-3 | 동기일 때 리스너 예외 → **다음 리스너 중단 + 호출자까지 전파** (STAGE 3 @Async 면 달라짐) |
| 1-4 | `ApplicationListener<E>` 인터페이스(= `ApplicationEvent` 상속 필요) vs `@EventListener`(불필요) — 동작은 같은 메커니즘 |
| 1-5 | payload-only: 상속 없이 String/record 발행, **payload 타입으로 매칭** (내부 `PayloadApplicationEvent` 래핑) |

### 자동 누적 로그

- [06-08 19:50] s1-1 · HelloEvent 발행 → 동기 리스너 호출 순서 println 확인
- [06-08 19:54] s1-2 · @Order로 리스너 호출 순서 제어 확인 (작은 값 먼저)
- [06-08 20:03] s1-3 · 동기 기준동작: 예외 전파 O, 체인 중단 O — STAGE3 @Async 면 전파 X 예정
- [06-08 20:10] s1-4 · ApplicationListener<E> vs @EventListener — 동작 동일(같은 메커니즘), 
  단 인터페이스는 클래스당 이벤트 1개 고정
- [06-08 20:24] s1-5 · payload-only — ApplicationEvent 상속없이 String/record 발행, 타입으로 매칭 (내부 PayloadApplicationEvent 래핑)
