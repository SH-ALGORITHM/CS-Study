# 1주차 — 멀티스레드에서 데이터가 망가지는 경험

이번 주제: 여러 스레드가 같은 변수를 만질 때 어떻게 망가지는지 직접 확인.
도메인은 본인 선택. 코드 모양은 거의 같음.

---

## 우선 알아둬야 할 단어 (시작 전 1분)

| 단어 | 풀어쓰면 |
|---|---|
| **race condition** | 두 스레드가 같은 변수 동시에 만져서 결과가 망가짐 |
| **atomicity (원자성)** | "한 번에 일어남" — 중간에 다른 스레드가 끼어들 수 없음 |
| **visibility (가시성)** | 한 스레드가 변수 바꿨는데 다른 스레드가 못 봄 (캐시 때문) |
| **JIT 워밍업** | JVM이 코드를 충분히 돌려야 빨라짐 — 측정 전에 미리 5,000번 돌려놓기 |


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 0. 개념 숙지 (도메인 무관, 모두 동일)
━━━━━━━━━━━━━━━━━━━━━━━━━━

## 필수 개념 (AI와 대화하며 숙지)
1. 프로세스 vs 스레드 (메모리 공유 관점)
2. JVM 메모리 영역 5가지 (Heap, Stack 등)
3. race condition / atomicity / visibility
4. 컨텍스트 스위칭 (스레드 간 전환)

## 자기 검증 (입으로 답할 수 있어야 STAGE 1 시작)
- [ ] 프로세스 vs 스레드 차이
- [ ] JVM 메모리 영역 5개 + 어디가 공유 / 어디가 스레드별
- [ ] race condition 예시 1개
- [ ] `count++` 가 한 번에 끝나는가
- [ ] 단일 스레드는 왜 망가지지 않는가
- [ ] atomicity 와 visibility 차이


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 1. 본인 도메인 선택
━━━━━━━━━━━━━━━━━━━━━━━━━━

같은 종류의 망가짐, 다른 도메인. 1개 선택.

### 숫자가 줄어드는 도메인 (값 차감형)
- **선착순 쿠폰** — 100개인데 105개 발급
- **콘서트 좌석** — 같은 좌석 두 명에게 팔림
- **재고 관리** — 1개 남았는데 두 명 결제 성공
- **좋아요 카운트** — 100명 눌렀는데 80만 카운트됨

### 참/거짓이 바뀌는 도메인 (덮어쓰기형)
- **출퇴근 기록** — 도장 두 번 찍힘 / 시각 덮어씀
- **이중 환불** — 한 결제에 환불이 두 번 처리됨

### STAGE 2를 깊게 가져갈 멤버
- **volatile 시나리오** — 마감 플래그가 다른 스레드에 안 보임
  (이 분기 선택자는 STAGE 1 가볍게 → STAGE 2 깊게. 박수진 권장.)


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 2. 코드 모양 (선택한 도메인에 따라 A 또는 B)
━━━━━━━━━━━━━━━━━━━━━━━━━━

## (A) 값 차감형 — 쿠폰 / 좌석 / 재고 / 좋아요

```java
public class [도메인명] {
    private int [공유자원] = 100;        // 잔고 / 좌석 / 쿠폰 / ...

    public boolean [동작]() {
        if ([공유자원] > 0) {              // ← 여기서 읽고
            [공유자원]--;                   // ← 여기서 쓰는 사이에 다른 스레드 끼어듦
            return true;
        }
        return false;
    }
}
```

## (B) 덮어쓰기형 — 출퇴근 / 환불

```java
public class [도메인명] {
    private boolean [상태] = false;      // checkedIn / refunded

    public boolean [동작]() {
        if (![상태]) {                     // ← 여기서 "아직 안 했네" 확인
            [상태] = true;                  // ← 여기서 "했음" 표시
            // 실제 처리 (도장 찍기 / 환불 호출)
            return true;
        }
        return false;
    }
}
```

## 도메인별 변수 매핑

| 도메인 | 클래스명 | 변수명 | 메서드 | 패턴 |
|---|---|---|---|---|
| 선착순 쿠폰 | `CouponEvent` | `availableCoupons` | `claim()` | A |
| 콘서트 좌석 | `ConcertBooking` | `availableSeats` | `reserve()` | A |
| 재고 관리 | `StockManager` | `stock` | `purchase()` | A |
| 좋아요 | `LikeCounter` | `count` | `like()` | A |
| 출퇴근 | `Attendance` | `checkedIn (bool)` | `checkIn()` | B |
| 이중 환불 | `RefundService` | `refunded (bool)` | `refund()` | B |


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 3. 동시에 여러 명 흉내내기 (모두 동일)
━━━━━━━━━━━━━━━━━━━━━━━━━━

```java
ExecutorService executor = Executors.newFixedThreadPool(50);
AtomicInteger successCount = new AtomicInteger(0);   // ⚠️ 결과 세는 도구일 뿐 (race 해결 아님)

for (int i = 0; i < 200; i++) {
    executor.submit(() -> {
        if (instance.[동작]()) {
            successCount.incrementAndGet();
        }
    });
}

executor.shutdown();
executor.awaitTermination(10, TimeUnit.SECONDS);

System.out.println("기대: 100 / 실제: " + successCount.get());
System.out.println("[공유자원]: " + instance.[공유자원]);
```

> 위 `AtomicInteger successCount` 는 **결과를 세는 용도**. 본인 도메인 코드의 race를 풀기 위해서는 STAGE 3 전까지 사용하지 말 것.


━━━━━━━━━━━━━━━━━━━━━━━━━━
환경
━━━━━━━━━━━━━━━━━━━━━━━━━━
- Java 21
- Spring Boot 안 띄움 (그냥 `main()` 메서드만)
- 동시성 흉내: `ExecutorService`


━━━━━━━━━━━━━━━━━━━━━━━━━━
STAGE 진행 가이드
━━━━━━━━━━━━━━━━━━━━━━━━━━

### [월 11:00 — Draft PR 마감 + 겪기 발표]

#### ▸ STAGE 1 — 망가짐 직접 보기 (필수)
- 목표: 위 코드로 race 발생 확인
- 결과물: `"기대 100 / 실제 105"` 같은 망가짐 로그
- 이 시점부터 "검색 금지" 키워드 적용

#### ▸ STAGE 2 — 가시성(visibility) 만나기 (필수)
- 목표: 한 스레드가 바꾼 값이 다른 스레드에 안 보이는 현상 직접 확인
- **별도 클래스로 만들 것** (도메인 클래스에 섞으면 다른 동기화 효과로 가려짐)

```java
class Worker extends Thread {
    boolean stopped = false;          // ← volatile 없이 시작

    public void run() {
        while (!stopped) {            // ← 빈 루프, 다른 작업 없이 깃발만 본다
            // 절대 println 넣지 말 것 — 출력 한 줄에 동기화 효과가 있어 가시성 문제가 사라짐
        }
    }
}

// main
Worker w = new Worker();
w.start();
Thread.sleep(1000);
w.stopped = true;                     // 메인이 "멈춰" 라고 했지만...
w.join();                             // 영원히 안 멈출 수 있음
```

##### ⚠️ 가시성 문제가 안 보일 때 (자주 그럼)
인텔/맥(M 시리즈) CPU + JVM 최적화 조합으로 가시성 문제가 가려질 수 있음. 안 보이면 순서대로:

1. `println` 같은 출력 다 제거했나? (출력 한 줄에 동기화 효과 있음)
2. 워밍업 시간 더 주기: `Thread.sleep(5000)` 으로 JVM 최적화 충분히 돌게
3. `-Xint` 옵션으로 인터프리터 강제 모드 — 안 보임 현상이 가장 잘 보임
4. 그래도 안 보이면 운영자 mention. 재현 안 되는 것 자체도 학습 자료.

### [목 11:00 — Ready PR 전환 + 코드 발표]

#### ▸ STAGE 3 — 측정 + 해결 (필수)
**※ 이 단계부터 금지 키워드 해제** — `synchronized` / `Atomic*` / `volatile` 사용 가능.
단 "직접 시도 후 적용" 룰 유지 (생각 → 가설 → 시도 → 결과 보고 → 적용).

##### 3-1. 스레드 수 변화 측정 (10 / 50 / 100 / 1000)

측정 항목:
- 도메인 통증 발생 횟수 (음수 / 중복 / 누락)
- 1000번 시도 중 몇 번 발생
- 단일 스레드 vs 멀티 스레드
- 해결책별 비교 (없음 vs synchronized vs Atomic)

측정 원칙:
- **JIT 워밍업**: 측정 전 5,000번 미리 실행 (결과 안 봄)
- **5회 평균**: 1회만 보면 GC 같은 영향 큼
- ⚠️ **측정 코드 중간에 `println` / log 절대 X** — 출력 한 줄에 동기화 효과가 섞여 측정 망가짐. 결과는 측정 끝난 후 한 번만 출력.

> 표 양식 / 도구 / 단일 vs 멀티 비교 가이드 → [`CONTRIBUTING.md`](../../CONTRIBUTING.md#6-측정-가이드-s3-단계)
> 1주차는 Java 단독이라 `println` 사용. 4주차부터 Spring Boot 들어오면서 SLF4J + Logback (`@Slf4j`) 으로 자연스럽게 전환됨.

##### 3-2. 해결책 적용 + 비교
- `synchronized` → `AtomicInteger` → (선택) `ReentrantLock`
- 각각: race 누락 0건? / TPS / 응답시간
- **"왜 synchronized가 더 느린가" 해석 필수** (답: 한 번에 한 스레드만 들어가니까 줄 서서 기다림)

#### ▸ STAGE 4 — Virtual Threads로 같은 race 재현 (선택)
- 목표: Java 21 Virtual Threads 직접 다뤄보고 일반 Thread와 비교
- Spring Boot는 4주차부터. 1주차 STAGE 4는 Java 단독 실험.

기존 코드의 한 줄만 바꾸면 됨:

```java
// 기존 (STAGE 1~3)
ExecutorService executor = Executors.newFixedThreadPool(50);

// STAGE 4 — Virtual Threads 버전
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

// 동시 시도 횟수도 크게 늘려보기 (가상 스레드는 1만 개도 가볍게)
for (int i = 0; i < 10_000; i++) {
    executor.submit(() -> instance.[동작]());
}
```

##### 측정 / 비교
- 같은 race가 Virtual Threads에서 더 자주 발생하는가? (동시성 훨씬 높음)
- Virtual Threads vs Platform Threads 응답시간 차이
- Virtual Threads에서도 `AtomicInteger` / `synchronized`로 동일하게 풀리는가? (메모리 모델 같음)

##### 학습 기록에 추가할 만한 주제
- "Virtual Threads 써봤어요?"
- "Virtual Threads와 일반 Thread 차이"
- "Virtual Threads가 race를 더 잘 만드나? 더 잘 푸나?"

> Virtual Threads = Java 21 표준 기능, Spring Boot 필요 없음. 4주차+ STAGE 4는 Spring 통합으로 바뀜.


━━━━━━━━━━━━━━━━━━━━━━━━━━
금지 키워드 — STAGE 1~2 동안만 (검색 금지)
━━━━━━━━━━━━━━━━━━━━━━━━━━
- `synchronized`, `ReentrantLock`, `@Lock`
- `AtomicInteger`, `AtomicLong`, `LongAdder`
  (단 STEP 3 코드의 `successCount`는 예외 — 결과 세는 도구)
- `volatile`
- `java.util.concurrent.locks.*`

**STAGE 3 시작과 함께 해제.** 단 직접 시도 → 가설 → 측정 → 적용 순서 유지.


━━━━━━━━━━━━━━━━━━━━━━━━━━
학습 기록 (자유)
━━━━━━━━━━━━━━━━━━━━━━━━━━

블로그 / 노션 / 본인 메모 등 형식 자유. 12주 끝나면 모의 면접 시간 있으니 평소 정리해두기 권장.

### 1주차 참고 질문 (답하고 싶은 만큼만)
- synchronized 와 volatile 차이
- JVM 메모리 영역 5가지
- 본인 도메인의 race condition 1분 설명
- count++ 가 왜 한 번에 끝나는 게 아닌가


━━━━━━━━━━━━━━━━━━━━━━━━━━
막힐 때
━━━━━━━━━━━━━━━━━━━━━━━━━━
1. 직접 시도 (`CLAUDE.md` 룰)
2. AI에 물어보기 — 3단계 힌트만 받음 (코드 직답 X)
3. 디스코드 `#질문` 채널 — 코드 + 에러 그대로
4. 그래도 안 되면 운영자 @멘션 (모임 1일 전부터)

특히 **STAGE 2 가시성 문제가 안 보이는 경우**: 위 "안 보일 때" 4단계 다 시도 후에도 안 보이면 운영자 mention. 재현 안 되는 게 학습 자료.
