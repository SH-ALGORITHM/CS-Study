# 10주차 — 캐시로도 못 막는 외부 호출 (HTTP 클라이언트 + Connection Pool + Timeout + Circuit Breaker)

이번 주제: 9 주차 캐시로 DB 부하를 막았다. 그런데 **결제 PG / 알림 / OAuth 같은 외부 API 는 캐시로 못 막는다** — 결과가 매번 다르고 호출 자체가 의미. 외부 API 가 5 초 지연되면 그 5 초 동안 톰캣 워커 스레드가 점유된다. 동시 100 요청 + 외부 5 초 지연 = 톰캣 워커 100 개 점유 → 다른 요청 거부 → 내 서버 장애. 외부 장애가 내 장애로 전파되는 자리다. 10 주차는 timeout + connection pool + circuit breaker 로 그 전파를 끊는다.

5 가지 학습 축:
- **HTTP 클라이언트 3 종** — RestTemplate (옛 표준, 유지보수) / WebClient (Reactive) / **RestClient (Spring 6.1+ 권장)**
- **Connection Pool** — TCP 3-way handshake 비용 / Keep-Alive 재사용 / maxTotal & maxPerRoute / 풀 고갈
- **Timeout 3 종** ★ — connect (소켓 연결) / read (응답 대기) / 전체. **외부 호출 = 반드시 timeout 명시**. 기본값은 무한
- **외부 지연 → 톰캣 스레드 풀 고갈 시뮬레이션** ★★ — 가짜 Slow 서버 + 의도적 5 초 지연으로 직접 재현
- **Circuit Breaker** (Resilience4j) — CLOSED / OPEN / HALF_OPEN 상태 머신. 빠른 실패 + 자동 복구 + Bulkhead (스레드 분리)

---

## 우선 알아둬야 할 단어 (시작 전 1 분)

| 단어 | 풀어쓰면 |
|---|---|
| **HTTP 클라이언트** | 외부 HTTP API 호출 라이브러리 |
| **RestTemplate** | Spring 의 옛 동기 HTTP 클라이언트. 유지보수 모드 (deprecated 아님) |
| **WebClient** | Spring 5+ Reactive (논블로킹). 비동기 / 스트리밍 |
| **RestClient** | **Spring 6.1+ 권장**. RestTemplate 의 후속. fluent API |
| **Connection Pool** | TCP 연결 재사용 풀. 매번 새 연결의 3-way handshake 비용 절감 |
| **Keep-Alive** | HTTP 1.1 기본. 한 TCP 연결로 여러 요청 |
| **maxTotal / maxPerRoute** | 풀 전체 / 호스트 별 최대 연결 수 |
| **connect timeout** | TCP 소켓 연결 시도 시간. 보통 1 ~ 3 초 |
| **read timeout** | 응답 데이터 읽기 시간. 도메인 특성에 따라 1 ~ 30 초 |
| **request timeout** | 전체 (connect + read). 외부 API 의 SLA 기준 |
| **무한 대기** | timeout 미설정 시 — JVM 이 영원히 기다림. 실무 장애 1 위 |
| **Circuit Breaker** | 장애 감지 → OPEN 상태 → 빠른 실패. 일정 시간 후 HALF_OPEN 으로 시도 |
| **Resilience4j** | Java 의 표준 회복성 라이브러리. Spring Boot 통합 쉬움 |
| **Retry** | 외부 일시 장애 자동 재시도 + Backoff |
| **Bulkhead** | 외부 호출용 스레드 풀 분리. 톰캣 워커 보호 |

> 📚 더 깊은 용어 (HTTP 2 / NoHttpResponseException / 5xx vs 4xx 처리 / Reactor 등) — [`terms.md`](terms.md) 참고.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 0. 개념 숙지 (도메인 무관)
━━━━━━━━━━━━━━━━━━━━━━━━━━

## 필수 개념

### 9 주차 → 10 주차 연결
1. **캐시로 못 막는 호출** — 결제 / 알림 / OAuth / 외부 시세 (변동 큼). 호출 자체가 의미
2. **외부 호출 = 내 스레드 점유** — 동기 호출 시 외부 응답 대기 동안 톰캣 워커 점유. 동시 N 요청 + 외부 t 초 = N×t 스레드·초

### HTTP 클라이언트 3 종 비교
3. **RestTemplate** — 옛 표준. 유지보수 모드 (deprecated 는 아님). 새 프로젝트 권장 X
4. **WebClient** — Spring 5+ Reactive. 논블로킹. 적은 스레드로 많은 동시 호출. 단 학습 곡선 (Reactor)
5. **RestClient** — Spring 6.1+. RestTemplate 의 fluent API 버전. 동기 + 가독성 좋음. **새 프로젝트 권장**

### Connection Pool 본질
6. **TCP 3-way handshake** — 매 호출마다 SYN → SYN-ACK → ACK 3 회 왕복. RTT 30ms 면 90ms 소요. Keep-Alive 로 1 회만
7. **Apache HttpClient 5 PoolingConnectionManager** — maxTotal (전체 한도) / maxPerRoute (호스트별 한도). 기본 2/route — 너무 작음
8. **Connection TTL** — 유휴 연결 만료 시간. 너무 길면 서버 측 끊기로 NoHttpResponseException

### Timeout 3 종 (★ 가장 중요)
9. **connect timeout** — 소켓 연결 시도. DNS / 방화벽 / 외부 다운 시 발동. 짧게 (1 ~ 3 초)
10. **read timeout** — 응답 데이터 대기. 외부 처리 시간 + 네트워크. 도메인 SLA 기준 (1 ~ 30 초)
11. **request timeout** (전체) — connect + read 의 상한. WebClient / RestClient 일부에서 지원
12. **timeout 미설정 = 영원히 대기** — 실무 장애 1 위. **항상 명시**

### 외부 지연 → 톰캣 스레드 풀 고갈
13. **시나리오** — 가짜 Slow 서버 (5 초 지연) + 톰캣 worker 10 + 동시 100 요청 → 10 개만 처리 + 90 거부 / 대기
14. **OSIV 와 같은 자리** (7 주차 회수) — DB 커넥션 점유 / 톰캥 워커 점유 둘 다 외부 대기에 의한 풀 고갈
15. **해결** — (a) timeout 명시 → 빠른 실패 / (b) WebClient 논블로킹 → 적은 스레드 / (c) Bulkhead → 외부용 풀 분리 / (d) Circuit Breaker → 외부 장애 빠른 차단

### Circuit Breaker (Resilience4j)
16. **상태 머신 3 단계** — CLOSED (정상) → 실패율 임계치 초과 → OPEN (빠른 실패) → 일정 시간 후 HALF_OPEN (시도) → 성공 = CLOSED / 실패 = OPEN
17. **빠른 실패의 가치** — OPEN 상태에서는 외부 호출 자체 안 함. 톰캣 워커 점유 0 → 다른 요청은 정상
18. **Bulkhead** — 외부 호출용 스레드 풀 분리. 톰캣 워커가 외부 응답 대기로 점유되는 걸 차단

## 자기 검증 (입으로)

**★ 관문 3**
- [ ] ★ 외부 API 지연이 톰캣 워커를 어떻게 점유하나 — 1 분 본인 말로
- [ ] ★ Timeout 3 종 (connect / read / request) 각각 의미 + 미설정 시 위험
- [ ] ★ Circuit Breaker 3 상태 + 각 상태에서 외부 호출 여부

**보너스**
- [ ] RestTemplate / WebClient / RestClient 차이 + 본인 도메인에 어느 쪽
- [ ] Connection Pool 의 maxTotal / maxPerRoute — 기본값이 위험한 이유
- [ ] Keep-Alive 의 NoHttpResponseException 함정
- [ ] 9 주차 캐시 + 10 주차 Circuit Breaker 결합 — 안전망 + 빠른 실패
- [ ] Bulkhead 가 톰캣 워커를 어떻게 보호하나
- [ ] 외부 API 의 5xx 응답 — Retry 가능 vs 불가능 구분


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 1. 본인 도메인 선택
━━━━━━━━━━━━━━━━━━━━━━━━━━

10 주차 학습 포인트는 **외부 API 호출이 자연스러운 도메인** 에서 잘 드러난다. 가짜 Slow 서버로 시뮬레이션 가능하므로 진짜 외부 API 키는 불필요.

## 후보 도메인 (12 개)

| # | 도메인 | 외부 호출 자연 | Timeout 결정 명확 | 면접 가치 | 메모 |
|---|---|---|---|---|---|
| 1 | **결제 PG** (`payment`) | ★★★ | ★★★ | ★★★ | Toss / Kakao 가짜 PG. **가장 정석**. 6, 7 주차 결제 연장 |
| 2 | **외부 알림** (`notify`) | ★★★ | ★★ | ★★ | Slack / FCM. 6 주차 이벤트 연장 |
| 3 | **환율 / 시세** (`rate`) | ★★★ | ★★ | ★★ | 9 주차 환율 연장. 캐시 + Circuit Breaker 결합 |
| 4 | **OAuth 로그인** (`oauth`) | ★★★ | ★★★ | ★★★ | Google / Kakao. 11 주차 인증 자연 연결 |
| 5 | **외부 검색** (`search`) | ★★★ | ★★ | ★★ | Elasticsearch / Algolia |
| 6 | **메일 발송** (`mail`) | ★★ | ★★ | ★★ | SES / Sendgrid |
| 7 | **배송 추적** (`delivery`) | ★★ | ★★ | ★★ | CJ / 한진 API |
| 8 | **AI / GPT API** (`ai`) | ★★★ | ★★★ | ★★★ | 응답 길어서 read timeout 학습 강 |
| 9 | **Geocoding** (`geo`) | ★★ | ★★ | ★★ | 지도 API |
| 10 | **SMS** (`sms`) | ★★ | ★★ | ★★ | NHN / NCloud |
| 11 | **파일 업로드** (`s3`) | ★★★ | ★★ | ★★ | S3. 큰 파일 → connection pool 학습 |
| 12 | **Webhook 발행** (`webhook`) | ★★★ | ★★★ | ★★ | 외부 서버에 콜백 — 실패 시 Retry |

## 도메인별 한 줄 시나리오

| # | 시나리오 |
|---|---|
| 1 | `paymentClient.charge(orderId, amount)` — 가짜 PG 5 초 지연. Timeout + Circuit Breaker + Retry |
| 2 | `slackClient.send(msg)` — 외부 알림. 6 주차 @TransactionalEventListener + Async 와 결합 |
| 3 | `rateClient.fetch(currency)` — 9 주차 캐시 + 10 주차 Circuit Breaker. 외부 장애 시 stale 반환 |
| 4 | `googleOauthClient.exchangeCode(code)` — 짧은 timeout. 5xx 시 즉시 실패 |
| 5 | `searchClient.search(q)` — Elasticsearch. 큰 응답 + read timeout |
| 6 ~ 12 | 비슷한 패턴 |

## 학습자 프로필별 추천

| 본인 상황 | 추천 |
|---|---|
| 입문자 | **1 결제 PG** — 면접 단골 + 시나리오 명확 |
| 9 주차 도메인 연장 | **3 환율** — 캐시 + Circuit Breaker 결합 |
| 6 주차 이벤트 연장 | **2 알림** — AFTER_COMMIT + 외부 호출 |
| 11 주차 (인증) 자연 브릿지 | **4 OAuth** |
| read timeout 학습 강 | **8 AI / GPT** — 응답 길고 변동 큼 |


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 2. 클래스 구조 매핑
━━━━━━━━━━━━━━━━━━━━━━━━━━

10 주차 example 은 **같은 JVM 안에 가짜 외부 서버 + 클라이언트** 둘 다 띄움. docker 없이 학습 가능.

| 컴포넌트 | 역할 |
|---|---|
| `SlowApiServer` | 의도적 지연 (1 ~ 5 초) 응답하는 Spring Boot 서버 (별도 포트) |
| `PaymentClient` | HttpClient (RestTemplate / WebClient / RestClient) 로 호출 |
| `PaymentService` | 비즈니스 로직 + 캐시 (9 주차) + Circuit Breaker (10 주차) |
| `LoadGenerator` | 동시 N 요청 시뮬레이션. CountDownLatch + Executor |

## measurements.md 형식

```
- [10-XX] s1-1 RestTemplate Pool 없이 100 호출 — ____ms (3-way handshake 누적)
- [10-XX] s1-2 RestClient + Pool — ____ms (Keep-Alive 효과)
- [10-XX] s2-2 톰캣 worker 10 + 동시 100 — 거부 / 대기 ____ 회
- [10-XX] s2-3 Timeout 없이 — 응답 시간 무한 / 톰캣 점유 영구
- [10-XX] s2-4 connect=1s read=3s — 빠른 실패 + 톰캣 회수
- [10-XX] s3-1 매번 새 연결 — connect 비용 ____ms × 100
- [10-XX] s3-2 Pool 적용 — connect 1 회 + 99 회 재사용
- [10-XX] s4-1 Circuit Breaker — 실패 N 회 후 OPEN 전환 확인
- [10-XX] s4-2 OPEN 상태 외부 호출 0 회 + 빠른 실패 ____ms
```


━━━━━━━━━━━━━━━━━━━━━━━━━━
환경
━━━━━━━━━━━━━━━━━━━━━━━━━━
- Java 21
- Spring Boot 3.2 — RestClient (6.1+), WebClient, RestTemplate
- Apache HttpClient 5 — Pooling
- Resilience4j — Spring Boot 통합

## build.gradle 추가

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'

    // WebClient (Reactive)
    implementation 'org.springframework.boot:spring-boot-starter-webflux'

    // Apache HttpClient 5 — Pool
    implementation 'org.apache.httpcomponents.client5:httpclient5:5.3'

    // Resilience4j — Circuit Breaker / Retry / Bulkhead
    implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'
}
```


━━━━━━━━━━━━━━━━━━━━━━━━━━
STAGE 진행 가이드
━━━━━━━━━━━━━━━━━━━━━━━━━━

## 예상 학습 시간

| 단계 | 시간 | 비고 |
|---|---|---|
| STAGE 1 (HTTP 클라이언트 3 종 비교 + Pool 적용) | 2 시간 | **화** |
| **STAGE 2 (Timeout + 톰캣 스레드 풀 고갈 시뮬레이션)** ★ | **2 ~ 3 시간** | **목**. 10 주차 가장 중요 |
| STAGE 3 (Connection Pool 본격) | 1 ~ 2 시간 | maxTotal / maxPerRoute |
| STAGE 4 (Circuit Breaker + Retry + Bulkhead) | 2 시간 | Resilience4j |
| **합계 (필수)** | **7 ~ 9 시간** | |
| STAGE 5 [여유] (캐시 + Circuit Breaker + 12 주차 브릿지) | 30 ~ 60 분 | |

### [화 11:00] — STAGE 1

#### ▸ STAGE 1 — HTTP 클라이언트 3 종 (필수)

##### 1-1. RestTemplate — 옛 표준 (참고용)

```java
RestTemplate rt = new RestTemplate();
String result = rt.getForObject("http://localhost:8081/api/slow", String.class);
```

**관찰 포인트**:
- 기본 `SimpleClientHttpRequestFactory` 사용 → `HttpURLConnection` 백엔드
- **풀 / route 제어 / eviction 없음** — `HttpURLConnection` 도 JVM `http.keepAlive=true` 기본으로 연결 캐시는 하지만, maxTotal / maxPerRoute / 만료 정책 같은 제어 X. 동시성에서 한계
- Spring 5.0+ 유지보수 모드. 새 프로젝트는 RestClient

##### 1-2. RestClient — Spring 6.1+ 권장

```java
@Bean
public RestClient restClient() {
    return RestClient.builder()
        .baseUrl("http://localhost:8081")
        .build();
}

// 사용
String result = restClient.get().uri("/api/slow").retrieve().body(String.class);
```

**관찰 포인트**:
- fluent API — 가독성 좋음
- RestTemplate 의 후속. 같은 동기. WebClient 의 Reactor 학습 부담 없음
- **기본 factory 는 클래스패스 자동 탐지**: Apache HttpComponents (httpclient5) → Jetty → `SimpleClientHttpRequestFactory`. **JDK HttpClient 는 자동 X** — `JdkClientHttpRequestFactory` 명시해야 사용. 따라서 httpclient5 의존성 있으면 자동으로 Apache 가 선택됨

##### 1-3. Apache HttpClient 5 + Pool 명시

```java
@Bean
public HttpClient httpClient() {
    PoolingHttpClientConnectionManager pool = new PoolingHttpClientConnectionManager();
    pool.setMaxTotal(100);
    pool.setDefaultMaxPerRoute(20);

    return HttpClients.custom()
        .setConnectionManager(pool)
        .build();
}

@Bean
public RestClient restClient(HttpClient http) {
    return RestClient.builder()
        .requestFactory(new HttpComponentsClientHttpRequestFactory((org.apache.hc.client5.http.classic.HttpClient) http))
        .baseUrl("http://localhost:8081")
        .build();
}
```

**관찰 포인트**:
- maxTotal = 풀 전체 한도 / maxPerRoute = 호스트별 한도
- 기본 maxPerRoute 가 2 — 너무 작아서 실무 위험
- Keep-Alive 로 TCP 재사용

##### 1-4. 측정 — Pool 효과

```java
// Pool 없이 100 회 vs Pool 있이 100 회 측정
long t1 = System.nanoTime();
for (int i = 0; i < 100; i++) plainClient.get(...);
long noPoolMs = (System.nanoTime() - t1) / 1_000_000;

long t2 = System.nanoTime();
for (int i = 0; i < 100; i++) pooledClient.get(...);
long pooledMs = (System.nanoTime() - t2) / 1_000_000;

System.out.println("No Pool: " + noPoolMs + "ms");
System.out.println("Pooled:  " + pooledMs + "ms");
```

RTT 가 작은 로컬에서는 차이 작음. 실 외부 호출 (RTT 30ms) 에서 차이 큼.


### [목 11:00] — STAGE 2 ~ 4

#### ▸ STAGE 2 — Timeout + 톰캣 스레드 풀 고갈 (★ 가장 중요)

##### 2-1. SlowApiServer 띄우기

```java
@RestController
public class SlowController {
    @GetMapping("/api/slow")
    public String slow() throws InterruptedException {
        Thread.sleep(5000);     // 의도적 5 초 지연
        return "ok";
    }
}
```

별도 main / 포트 8081 로 띄움.

##### 2-2. 톰캣 worker 10 + 동시 100 요청

`application.properties`:
```properties
server.port=8080
server.tomcat.threads.max=10
```

```java
// /api/charge 가 SlowApiServer 호출 (5 초 응답 대기)
@GetMapping("/api/charge")
public String charge() {
    return restClient.get().uri("/api/slow").retrieve().body(String.class);
}
```

부하 시뮬레이션 — 100 동시 요청:
```java
ExecutorService pool = Executors.newFixedThreadPool(100);
CountDownLatch go = new CountDownLatch(1);
for (int i = 0; i < 100; i++) {
    pool.submit(() -> {
        try { go.await(); } catch (InterruptedException e) { return; }
        // /api/charge 호출
    });
}
go.countDown();
```

**관찰 포인트**:
- 톰캣 worker 10 → 처음 10 개만 처리 시작. 나머지 90 은 대기 큐 또는 거부
- 외부 호출 5 초 동안 워커 점유 → 5 초 동안 다른 요청 0 처리
- 결과 — 100 요청 처리에 50 초 이상

##### 2-3. Timeout 없으면 — 무한 대기 함정

```java
// connect / read timeout 미설정
RestClient rt = RestClient.create();
```

**관찰 포인트**:
- SlowApiServer 가 응답 안 보내면 — JVM 영원히 대기
- 톰캣 워커 영원히 점유 → 새 요청 다 거부
- 외부 서버 다운 = 내 서버 다운. **실무 장애 1 위**

##### 2-4. Timeout 명시

```java
@Bean
public RestClient restClient() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout((int) Duration.ofSeconds(1).toMillis());
    factory.setReadTimeout((int) Duration.ofSeconds(3).toMillis());
    return RestClient.builder()
        .requestFactory(factory)
        .baseUrl("http://localhost:8081")
        .build();
}
```

**관찰 포인트**:
- read=3 초 → SlowApi 5 초 지연 시 3 초 후 `ResourceAccessException` 발생
- 톰캣 워커 3 초 후 회수 → 다른 요청 처리 가능
- 외부 SLA 기준으로 결정 (PG 보통 5 초, 알림 1 초 등)


#### ▸ STAGE 3 — Connection Pool 본격

##### 3-1. Pool 없이 — 매번 새 연결

```java
// 매 호출마다 새 SimpleClientHttpRequestFactory → 새 TCP 연결
for (int i = 0; i < 100; i++) {
    new RestTemplate().getForObject("https://api.example.com", String.class);
    // TCP handshake (SYN/SYN-ACK/ACK) + TLS handshake 매번
}
```

##### 3-2. Pool 적용 — Keep-Alive 재사용

```java
PoolingHttpClientConnectionManager pool = new PoolingHttpClientConnectionManager();
pool.setMaxTotal(100);
pool.setDefaultMaxPerRoute(20);

CloseableHttpClient client = HttpClients.custom()
    .setConnectionManager(pool)
    .evictExpiredConnections()
    .evictIdleConnections(TimeValue.ofSeconds(30))
    .build();

// 같은 호스트면 같은 TCP 연결 재사용
```

##### 3-3. maxTotal / maxPerRoute 결정

| 시나리오 | 권장 |
|---|---|
| 단일 외부 API | maxTotal = maxPerRoute = 예상 동시 호출 수 |
| 여러 외부 API | maxTotal = 전체 한도 / maxPerRoute = 각 호스트별 한도 |
| 기본값 (2 / 20) | **너무 작음**. 실무 위험 |

##### 3-4. Pool 고갈 시뮬레이션

```java
// pool maxTotal=5, 동시 10 요청
// 5 개만 호출 시작 / 5 개는 풀에서 연결 대기 → 응답 지연
```


#### ▸ STAGE 4 — Circuit Breaker + Retry + Bulkhead (필수)

##### 4-1. Resilience4j CircuitBreaker

```java
@CircuitBreaker(name = "payment", fallbackMethod = "fallback")
public String charge(String orderId) {
    return restClient.get().uri("/api/slow").retrieve().body(String.class);
}

public String fallback(String orderId, Throwable t) {
    return "FAILED: " + t.getMessage();
}
```

`application.properties`:
```properties
resilience4j.circuitbreaker.instances.payment.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.payment.sliding-window-size=10
resilience4j.circuitbreaker.instances.payment.wait-duration-in-open-state=10s
```

**관찰 포인트**:
- 슬라이딩 윈도우 10 회 중 5 회 (50%) 실패 → OPEN 전환
- OPEN 상태에서 호출 = fallback 즉시. 외부 호출 0 회
- 10 초 후 HALF_OPEN → 1 회 시도 → 성공 = CLOSED / 실패 = OPEN

##### 4-2. Retry — 일시 장애 자동 재시도

```java
@Retry(name = "payment")
@CircuitBreaker(name = "payment", fallbackMethod = "fallback")
public String charge(String orderId) { /* ... */ }
```

```properties
resilience4j.retry.instances.payment.max-attempts=3
resilience4j.retry.instances.payment.wait-duration=500ms
resilience4j.retry.instances.payment.exponential-backoff-multiplier=2
```

**관찰 포인트**:
- 일시 5xx — Retry 가 자동 재시도. 영구 장애는 Circuit Breaker 가 차단
- Backoff — 500ms → 1s → 2s

##### 4-3. Bulkhead — 외부 호출용 스레드 풀 분리

```java
@Bulkhead(name = "payment", type = Bulkhead.Type.THREADPOOL)
public CompletableFuture<String> charge(String orderId) { /* ... */ }
```

```properties
resilience4j.thread-pool-bulkhead.instances.payment.max-thread-pool-size=10
resilience4j.thread-pool-bulkhead.instances.payment.core-thread-pool-size=5
```

**관찰 포인트**:
- 외부 호출이 별도 스레드 풀에서 — 톰캣 워커는 보호됨
- 외부 풀 고갈 시 톰캣 워커가 직접 차단 (fast fail)


━━━━━━━━━━━━━━━━━━━━━━━━━━
─── 선택 ───
━━━━━━━━━━━━━━━━━━━━━━━━━━

### [선택] ▸ STAGE 5 — 캐시 + Circuit Breaker + 12 주차 브릿지

##### 5-1. 9 주차 캐시 + 10 주차 Circuit Breaker 결합

```java
@Cacheable("rates")
@CircuitBreaker(name = "rate", fallbackMethod = "lastKnown")
public BigDecimal fetchRate(String currency) {
    return rateClient.get(...);
}

public BigDecimal lastKnown(String currency, Throwable t) {
    return lastKnownRate.get(currency);   // 캐시된 마지막 값
}
```

- 캐시 = 평소 빠른 응답 / Circuit Breaker = 외부 장애 시 빠른 실패 + fallback

##### 5-2. 12 주차 (관측) 예고

Circuit Breaker / Retry / Pool 모두 **메트릭 노출**. Micrometer + Prometheus + Grafana 로 모니터링 = 12 주차


━━━━━━━━━━━━━━━━━━━━━━━━━━
금지 키워드 — STAGE 1 ~ 2 동안
━━━━━━━━━━━━━━━━━━━━━━━━━━
- Reactive Streams (Mono / Flux) 의 깊은 학습 — WebClient 는 살짝만
- Spring Cloud Gateway / API Gateway — 본 학습 후
- gRPC — 본 학습 후
- Spring Cloud OpenFeign — 학습 후 익히기 (RestClient 후)
- HTTP/2 / HTTP/3 — 학습 범위 밖


━━━━━━━━━━━━━━━━━━━━━━━━━━
학습 기록
━━━━━━━━━━━━━━━━━━━━━━━━━━

### 9 주차 회상 — 10 주차로

| 9 주차 | 10 주차 |
|---|---|
| 캐시 = DB 차단 | Pool + Timeout = 외부 호출 제어 |
| Stampede (TTL 만료 동시 miss) | 외부 장애 시 스레드 풀 고갈 — 같은 자리 |
| Cache stampede 분산락 해결 | Circuit Breaker 로 외부 장애 격리 |
| 캐시로 못 막는 호출 = 외부 결제 | 10 주차 본론 |

### 면접 단골
- **"외부 API 지연이 톰캣 워커를 어떻게 점유하나"** — 동기 호출 시 응답 대기 동안 스레드 점유. 동시 N + 5 초 = N 스레드·5 초
- **"Timeout 3 종"** — connect / read / request. 미설정 = 무한 대기
- **"Circuit Breaker 3 상태"** — CLOSED / OPEN / HALF_OPEN. OPEN 상태는 외부 호출 안 함
- **"Connection Pool 의 효과"** — TCP handshake 절감 + Keep-Alive 재사용
- **"RestTemplate / WebClient / RestClient"** — 유지보수 / Reactive / Spring 6.1+ 권장
- **"Bulkhead"** — 외부 호출 스레드 풀 분리. 톰캣 워커 보호
- **"Retry 와 Circuit Breaker 의 관계"** — Retry = 일시 장애 / Circuit Breaker = 영구 장애 격리
- **"OSIV vs 외부 호출 풀 고갈"** — 둘 다 외부 대기로 풀 점유. 7 주차 / 10 주차 같은 자리
- **"`@Retry` + `@CircuitBreaker` 같이 붙으면 누가 먼저?"** — Retry (바깥) → CircuitBreaker (안). 5 주차 `@Order` advice 안-밖과 같은 메커니즘
- **"HALF_OPEN 에서 무한 시도하는가"** — X. `permittedNumberOfCallsInHalfOpenState` 만큼만 ("간보기"). 결과로 CLOSED / OPEN

### 실무 확장 화두
- **HTTP/2 의 멀티플렉싱** — 한 TCP 연결로 여러 요청 동시. Connection Pool 의미 줄어듦
- **timeout 의 클라이언트 / 서버 비대칭** — 클라이언트 timeout < 서버 timeout 이어야. 반대면 좀비 응답
- **NoHttpResponseException** — Keep-Alive 의 함정. 서버가 idle 연결 끊었는데 클라이언트는 모름. validateAfterInactivity 또는 짧은 idle TTL
- **Resilience4j vs Hystrix** — Hystrix 는 deprecated. Resilience4j 가 표준
- **5xx 처리** — 503 Service Unavailable → Retry / 500 Internal → Retry 안 함 / 401 / 403 → Retry 안 함
- **Reactive 의 가치** — WebClient = 적은 스레드로 많은 동시 호출. 단 도메인 전체 Reactive 화 필요 (학습 곡선)
- **외부 호출의 idempotency** — Retry 시 같은 요청 2 번 발생. PG 결제 같은 곳은 idempotency-key 필수
- **9 주차 캐시 + 10 주차 stale-while-revalidate** — 캐시 만료 시 백그라운드 갱신 + 옛 값 반환
- **Pool + Timeout 혼용 함정** — Apache HttpClient 5 쓸 때 Timeout 은 `SimpleClientHttpRequestFactory` setter 가 아니라 **`RequestConfig` / `ConnectionConfig`** 단에서. 둘 섞으면 한쪽 무시. 실무 가장 흔한 실수
- **Resilience4j 어노테이션 체이닝 순서 (5 주차 회수)** — `@Retry` + `@CircuitBreaker` 같이 붙으면 기본 `Retry (바깥) → CircuitBreaker (안)`. 재시도 후에도 실패하면 CB 통계에 쌓임. 5 주차 `@Order` advice 안-밖과 같은 메커니즘
- **HALF_OPEN 의 "간보기" 한도** — `permittedNumberOfCallsInHalfOpenState` (기본 10). OPEN 후 wait-duration 지나 HALF_OPEN 되면 무한 시도 X. 딱 N 회만 보내고 성공률로 CLOSED / OPEN


━━━━━━━━━━━━━━━━━━━━━━━━━━
막힐 때
━━━━━━━━━━━━━━━━━━━━━━━━━━

**Timeout 이 안 먹는 것 같음**:
1. Builder 의 timeout 메서드 호출했는가
2. requestFactory 까지 변경했는가 (RestClient 는 factory 통해)
3. 네트워크 단에서 OS keepalive 가 다른 값일 수 있음
4. **Apache HttpClient 5 + Pool + Timeout 혼용 함정** — `SimpleClientHttpRequestFactory.setReadTimeout` 은 Apache 클라이언트엔 적용 안 됨. Apache 쪽 timeout 은 `RequestConfig.Builder.setResponseTimeout` + `ConnectionConfig.Builder.setConnectTimeout` 으로 설정 후 `PoolingHttpClientConnectionManager` 에 주입

**Circuit Breaker 가 OPEN 안 됨**:
1. 슬라이딩 윈도우 사이즈 / failure rate threshold 확인
2. 예외 타입이 recordExceptions 에 포함되었나
3. minimum-number-of-calls 미달이면 통계 안 함

**Connection Pool 고갈 — Timeout Waiting for Connection**:
1. maxTotal / maxPerRoute 부족
2. 연결 release 안 했음 (try-with-resources 누락)
3. Pool 누수 — 응답 처리 후 connection close 호출

**Tomcat worker 가 다 점유됨**:
1. 외부 호출 timeout 미설정
2. 동기 호출 + 외부 지연 — Bulkhead 적용 검토
3. WebClient (Reactive) 로 전환 검토 — 학습 곡선 큼
