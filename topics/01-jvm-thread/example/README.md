# 1주차 예시 코드 — 은행 잔고 도메인

scenario.md의 7개 도메인(쿠폰/좌석/재고/...)과 **별개로** 만든 참고 코드입니다.
교과서적인 예시 "은행 잔고"로, 4단계 흐름이 어떻게 연결되는지 보여줍니다.

> ⚠️ **베끼지 마시고 본인 도메인으로 변환하세요.** 이건 "이런 식으로 흘러가는구나" 감을 잡는 용도.

## 무엇이 있나

| 파일 | 단계 | 보는 것 |
|---|---|---|
| `src/main/java/BankAccount.java` | 공통 | 도메인 클래스 (race 발생 가능한 단순 모양) |
| `src/main/java/Stage1Race.java` | s1 | 다중 스레드로 race 발생 직접 보기 |
| `src/main/java/Stage2Visibility.java` | s2 | stop flag 무한 루프 — visibility 위반 직접 보기 |
| `src/main/java/Stage3Measurement.java` | s3 | 스레드 수 변화 측정 + synchronized/Atomic 비교 |
| `src/main/java/Stage4VirtualThreads.java` | s4 (선택) | Virtual Threads로 같은 race 더 격렬하게 |
| `build.gradle` | (설정) | IntelliJ가 Java 프로젝트로 인식하게 만드는 최소 설정 |

## 어떻게 실행

### IntelliJ로 (CSstudy 통째로 열기 — 추천)

CSstudy 루트에 멀티 모듈 Gradle 셋업이 되어 있어서, **CSstudy 폴더 한 번 열면 example과 4주차+ 모든 멤버 폴더가 자동으로 인식**됩니다.

1. IntelliJ에서 `~/Desktop/CSstudy` 열기 (또는 이미 열려있으면 우측 하단 **Reload Gradle Project** 클릭)
2. Gradle sync 완료 대기 (1~2분, 첫 실행 시 JDK 21 자동 다운로드 가능)
3. 좌측 프로젝트 트리에서 `topics → 01-jvm-thread → example → src/main/java → Stage1Race.java` 열기
4. main 메서드 옆 **녹색 ▶** 클릭
5. 콘솔에 결과 출력

### 터미널로 (선택)

```bash
cd topics/01-jvm-thread/example
javac -d out src/main/java/*.java
java -cp out Stage1Race
```

> 외부 라이브러리 의존성 없음 (Java 표준 라이브러리만 사용).

## 본인 도메인으로 변환할 때

| 은행 잔고 | → | 본인 도메인 (예: 쿠폰) |
|---|---|---|
| `BankAccount` | → | `CouponEvent` |
| `balance` | → | `availableCoupons` |
| `withdraw(int amount)` | → | `claim()` |
| `잔고 100원, 50원씩 출금` | → | `쿠폰 100개, 1개씩 청구` |

**구조는 같음, 단어만 달라짐.**

## 단계별 학습 목표

```
[금~일 14:00]   s1 + s2 — Stage1Race.java + Stage2Visibility.java 실행해보고
                본인 도메인으로 변환해서 race + visibility 둘 다 재현
                → 일 14:00까지 Draft PR 마감
                → 월 모임에서 Draft PR 화면 띄우고 시도 발표
                ⚠️ visibility 안 보일 수 있음 — scenario.md "안 보일 때" 4단계 시도

[화~수]         s3 — Stage3Measurement.java 보고, 본인 측정 코드 작성
                MeasurementLog.save() 호출 → measurements.md 자동 누적

[수 23:59까지]  PR을 Draft → Ready로 전환

[목요일 모임]   s3 측정 결과 발표 → 운영자가 머지

[여유 있으면]   s4 — Stage4VirtualThreads.java 보고 본인 도메인에도 적용
```

## 주의사항

- 본인 폴더(`members/{본인이름}/`)에 본인 도메인 코드 작성 — 여기 example/는 참고용
- 측정 코드 중간에 `println` 절대 X — 출력 한 줄에 동기화 효과
- AI에게 "이 코드 짜줘" 금지 — 본인이 시도 30분 후에 힌트 받기 (`CLAUDE.md` 룰)
