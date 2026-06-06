# 12주차 — 시스템이 지금 어떻게 동작하는지 알기 (Observability + Micrometer + Prometheus + Grafana)

이번 주제 — 시리즈의 마지막. 1 ~ 11 주차 내내 **시스템을 어떻게 잘 만들 것인가** 였다면, 12 주차는 **그 시스템이 지금 어떻게 동작하는지** 를 알아내는 자리. 어디서 느린가 / 어디서 실패하나 / 부하 한계는 어디인가. 1 ~ 11 주차 매 자리마다 "메트릭 노출" 이 언급됐다. 12 주차에서 한꺼번에 회수해서 한 대시보드에 그린다.

5 가지 학습 축:
- **관측 3 축** (Metrics / Logs / Traces) — 각각 언제 쓰나
- **Spring Boot Actuator + Micrometer** ★ — JVM / GC / HTTP 자동 노출. `/actuator/prometheus`
- **커스텀 메트릭** ★ — Counter (누적) / Gauge (현재) / Timer (시간 분포) / DistributionSummary (일반 분포)
- **Prometheus + Grafana** — 시계열 DB + 시각화. docker compose 한 번에
- **Trace ID + MDC + Micrometer Tracing** — 한 요청의 모든 로그를 trace id 로 묶어 추적

---

## 우선 알아둬야 할 단어 (시작 전 1 분)

| 단어 | 풀어쓰면 |
|---|---|
| **Observability** (관측 가능성) | 외부에서 시스템 내부 상태를 추론할 수 있는 정도 |
| **Metrics** | 수치 시계열. CPU / Heap / 요청 수 / 응답 시간 |
| **Logs** | 시점별 텍스트. 디버깅 / 감사 |
| **Traces** | 한 요청의 분산 시스템 흐름. spanId / traceId |
| **3 Pillars** | 위 셋. Metrics / Logs / Traces |
| **Spring Boot Actuator** | Spring 기본. `/actuator/health` / `/metrics` / `/prometheus` |
| **Micrometer** | JVM 메트릭 facade (SLF4J 의 메트릭 버전). registry 교체로 백엔드 바꿈 |
| **Prometheus** | 시계열 DB. pull 모델 — scrape interval 마다 끌어옴 |
| **Grafana** | 시각화. Prometheus / Loki / Tempo 등 데이터소스 통합 |
| **Counter** | 누적값. 요청 횟수 / 에러 횟수 (단조 증가) |
| **Gauge** | 현재 값. 큐 사이즈 / 활성 연결 / Heap 사용량 |
| **Timer** | 시간 분포. count + sum + max + 백분위 (p50/p95/p99) |
| **DistributionSummary** | 일반 분포. 응답 크기 / 처리 단위 수 |
| **MDC** (Mapped Diagnostic Context) | SLF4J 의 ThreadLocal context. 로그에 traceId 자동 |
| **Micrometer Tracing** | 옛 Spring Cloud Sleuth 후속. Brave / OpenTelemetry 백엔드 |

> 📚 더 깊은 용어 — [`terms.md`](terms.md) 참고.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 0. 개념 숙지
━━━━━━━━━━━━━━━━━━━━━━━━━━

## 필수 개념

### 11 주차 → 12 주차
1. **시스템을 잘 만든 후의 다음 단계** — 그 시스템이 실제로 어떻게 동작하나. 인덱스로 ms 단위로 줄였어도 모니터링 없이는 운영 불가
2. **1 ~ 11 주차 회수** — 매 주차마다 "메트릭 노출" 자리가 있었음. 12 주차에 한 대시보드

### 관측 3 축 (3 Pillars)
3. **Metrics** = 수치 시계열. CPU / 응답 시간 / 요청 수. 집계가 본질. **"시스템이 지금 건강한가?"**
4. **Logs** = 시점별 텍스트. **"무엇이 / 언제 일어났나?"**. 디버깅
5. **Traces** = 한 요청의 분산 시스템 흐름. **"이 요청이 어디를 거쳤나?"**. 마이크로서비스에서 필수
6. **셋 다 필요** — 메트릭이 이상 발견 → 트레이스로 어디서 → 로그로 무엇이

### Spring Boot Actuator + Micrometer
7. **Actuator** — Spring Boot 기본 모니터링 / 관리 엔드포인트. `/actuator/health` `/actuator/metrics` `/actuator/prometheus`
8. **Micrometer** — JVM 메트릭 facade. SLF4J 가 로그 facade 라면 Micrometer 는 메트릭 facade. registry 교체로 백엔드 (Prometheus / Datadog / CloudWatch) 바꿈
9. **자동 노출 메트릭** — JVM (heap / GC / threads) / HTTP (request count / duration) / DataSource (active / idle) / Tomcat (worker)

### 커스텀 메트릭 4 타입 (★ 핵심)
10. **Counter** — 누적값. 단조 증가. 예: `orders_total` / `errors_total`. PromQL `rate()` 로 초당
11. **Gauge** — 현재 순간 값. 증감 OK. 예: `queue_size` / `active_users` / `heap_used`
12. **Timer** — 시간 분포. count + sum + max + p50/p95/p99. 예: `payment_duration_seconds`
13. **DistributionSummary** — 일반 분포 (Timer 의 일반화). 예: `response_size_bytes`

### Prometheus + Grafana
14. **Prometheus pull 모델** — 서비스 가 메트릭 push 가 아니라 Prometheus 가 `/actuator/prometheus` 를 scrape (보통 15 초마다)
15. **PromQL** — Prometheus 쿼리 언어. `rate(orders_total[1m])` / `histogram_quantile(0.95, ...)` / `sum by (status) (...)`
16. **Grafana 대시보드** — Spring Boot 표준 (ID 11378) import 하면 즉시. 본인 도메인은 직접

### Trace ID + MDC + Tracing
17. **MDC** — SLF4J 의 ThreadLocal map. 로그 패턴에 `%X{traceId}` 로 포함 가능
18. **Micrometer Tracing** — Spring Boot 3+ 의 표준. Brave (Zipkin 호환) 또는 OpenTelemetry 백엔드
19. **요청 흐름 추적** — 한 traceId 로 모든 로그를 grep 가능 → 한 요청에서 무슨 일이 일어났나 한눈에

## 자기 검증 (입으로)

**★ 관문 3**
- [ ] ★ 관측 3 축 — Metrics / Logs / Traces 각각 언제
- [ ] ★ Counter / Gauge / Timer / DistributionSummary 차이 + 본인 도메인 예 1 개씩
- [ ] ★ Prometheus pull 모델 + scrape — 왜 push 가 아닌가

**보너스**
- [ ] Actuator + Micrometer 의 자동 노출 메트릭 5 가지
- [ ] PromQL 의 rate / sum / histogram_quantile 본인 예
- [ ] 1 ~ 11 주차에서 메트릭 회수 가능 자리 본인 답
- [ ] MDC traceId 로 한 요청 grep — 분산 시스템에서 필수인 이유
- [ ] Actuator 엔드포인트 운영 보안 (11 주차 회수)


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 1. 본인 도메인 선택
━━━━━━━━━━━━━━━━━━━━━━━━━━

12 주차는 **본인 도메인에 메트릭을 심는 자리**. 모든 도메인에 적용. 1 ~ 11 주차 본인 도메인이 그대로.

## 메트릭 심을 자리 매트릭스

| 1 ~ 11 주차 | 12 주차 메트릭 |
|---|---|
| 3 주차 분산락 | `lock_wait_seconds` (Timer) / `lock_acquire_total{result=...}` (Counter) |
| 5 주차 @Audited / @Timed | 자작 Timer + Counter 그대로 |
| 6 주차 publishEvent | `events_published_total` (Counter) / `async_queue_size` (Gauge) |
| 7 주차 N+1 | `db_query_total` (Counter) / `n_plus_one_count_total` (Counter — 직접 감지) |
| 8 주차 인덱스 | DB exporter (별도 — pg_exporter) |
| 9 주차 캐시 | `cache_hits_total` / `cache_misses_total` / `cache_hit_ratio` (Gauge) |
| 10 주차 HTTP | `http_client_duration_seconds` (Timer) / Circuit Breaker 상태 (Gauge) |
| 11 주차 Auth | `auth_success_total` / `auth_failure_total{reason=...}` (Counter) |


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 2. 클래스 구조 매핑
━━━━━━━━━━━━━━━━━━━━━━━━━━

`MeterRegistry` 주입 → Counter / Gauge / Timer 등록.

```java
@Service
public class OrderService {
    private final Counter orderCounter;
    private final Timer paymentTimer;

    public OrderService(MeterRegistry registry) {
        this.orderCounter = registry.counter("orders_total", "status", "ok");
        this.paymentTimer = registry.timer("payment_duration_seconds");
    }

    public void placeOrder() {
        orderCounter.increment();
        paymentTimer.record(() -> externalPg.charge());
    }
}
```


━━━━━━━━━━━━━━━━━━━━━━━━━━
환경
━━━━━━━━━━━━━━━━━━━━━━━━━━
- Java 21
- Spring Boot 3.2 + Actuator + Micrometer Prometheus
- Prometheus 2.x + Grafana 10 — docker compose

## build.gradle

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'

    // Micrometer Prometheus 백엔드
    implementation 'io.micrometer:micrometer-registry-prometheus'

    // (선택) Tracing — STAGE 4
    implementation 'io.micrometer:micrometer-tracing-bridge-brave'
}
```

## application.properties

```properties
# Actuator — 학습용 전부 노출. 운영은 인증 필수 (11 주차 회수)
management.endpoints.web.exposure.include=*
management.endpoint.health.show-details=always

# Micrometer 히스토그램 (Timer p95/p99 자동)
management.metrics.distribution.percentiles-histogram.http.server.requests=true
management.metrics.distribution.percentiles.http.server.requests=0.5,0.95,0.99

# Tracing — STAGE 4 (100% sampling 학습용)
management.tracing.sampling.probability=1.0
```

## docker-compose.yml (Prometheus + Grafana)

```yaml
services:
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
```

## prometheus.yml

```yaml
scrape_configs:
  - job_name: 'spring'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 5s
    static_configs:
      - targets: ['host.docker.internal:8080']
```


━━━━━━━━━━━━━━━━━━━━━━━━━━
STAGE 진행 가이드
━━━━━━━━━━━━━━━━━━━━━━━━━━

## 예상 학습 시간

| 단계 | 시간 | 비고 |
|---|---|---|
| STAGE 1 (Actuator 기본 — health / metrics / prometheus) | 1 시간 | **화** |
| **STAGE 2 (커스텀 Counter / Gauge / Timer / @Timed)** ★ | **2 시간** | **목**. 가장 중요 |
| STAGE 3 (Prometheus + Grafana docker — 시각화) | 1 ~ 2 시간 | |
| STAGE 4 [살짝] Trace ID + MDC | 30 분 | |
| **합계** | **5 ~ 7 시간** | 시리즈 마지막, 살짝 짧게 |
| STAGE 5 [여유] 1 ~ 11 주차 회수 본인 도메인 적용 | 자유 | 본 학습 |


### [화 11:00] — STAGE 1

#### ▸ STAGE 1 — Actuator 기본 (필수)

##### 1-1. Actuator 엔드포인트

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP","components":{"diskSpace":..., "ping":...}}

curl http://localhost:8080/actuator/metrics
# {"names":["jvm.memory.used","http.server.requests",...]}

curl 'http://localhost:8080/actuator/metrics/jvm.memory.used?tag=area:heap'
# {"name":"jvm.memory.used","measurements":[{"statistic":"VALUE","value":1.234E8}], ...}
```

##### 1-2. `/actuator/prometheus` — Prometheus 형식

```
# HELP jvm_memory_used_bytes The amount of used memory
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",id="G1 Eden Space"} 1.234E8
jvm_memory_used_bytes{area="heap",id="G1 Old Gen"} 5.678E7
...

# HELP http_server_requests_seconds_count
# TYPE http_server_requests_seconds_count counter
http_server_requests_seconds_count{method="GET",status="200",uri="/api/hello"} 42
```

**관찰 포인트**:
- HTTP 요청 / JVM / GC / Tomcat / DataSource — 자동
- tag (label) — `method` / `status` / `uri` 별로 분리됨

##### 1-3. 자동 노출 메트릭 매트릭스

| 메트릭 | 의미 |
|---|---|
| `jvm.memory.used` | Heap / Non-heap |
| `jvm.gc.pause` | GC pause time |
| `jvm.threads.live` | 활성 스레드 |
| `system.cpu.usage` | 시스템 CPU |
| `process.cpu.usage` | 프로세스 CPU |
| `http.server.requests` | HTTP 요청 (Timer) |
| `tomcat.threads.busy` | 톰캣 워커 사용 중 |
| `hikaricp.connections.active` | DB 커넥션 활성 |


### [목 11:00] — STAGE 2 ~ 4

#### ▸ STAGE 2 — 커스텀 메트릭 (필수, **12 주차 가장 중요**)

##### 2-1. Counter — 누적값

```java
@Service
public class OrderService {
    private final Counter successCounter;
    private final Counter failureCounter;

    public OrderService(MeterRegistry registry) {
        this.successCounter = Counter.builder("orders_total")
            .tag("result", "success")
            .description("성공한 주문 수")
            .register(registry);
        this.failureCounter = Counter.builder("orders_total")
            .tag("result", "failure")
            .register(registry);
    }

    public void placeOrder() {
        try {
            // ...
            successCounter.increment();
        } catch (Exception e) {
            failureCounter.increment();
            throw e;
        }
    }
}
```

**관찰 포인트**:
- 단조 증가만. 절대 감소 X
- PromQL `rate(orders_total[1m])` 로 초당 비율
- tag 로 분리 — `orders_total{result="success"}` / `orders_total{result="failure"}`

##### 2-2. Gauge — 현재값

```java
private final AtomicInteger activeOrders = new AtomicInteger(0);

public OrderService(MeterRegistry registry) {
    Gauge.builder("active_orders", activeOrders, AtomicInteger::get)
        .description("현재 처리 중 주문 수")
        .register(registry);
}

public void placeOrder() {
    activeOrders.incrementAndGet();
    try { /* ... */ } finally { activeOrders.decrementAndGet(); }
}
```

**관찰 포인트**:
- 현재 값 — 증감 OK
- 큐 사이즈 / 활성 연결 / 풀 사용량에 적합
- Gauge 가 참조하는 객체가 GC 되면 안 됨 (`AtomicInteger` 필드로 유지)

##### 2-3. Timer — 시간 분포

```java
private final Timer paymentTimer;

public OrderService(MeterRegistry registry) {
    this.paymentTimer = Timer.builder("payment_duration_seconds")
        .publishPercentiles(0.5, 0.95, 0.99)        // 앱 계산 quantile (시계열 quantile="0.95")
        .publishPercentileHistogram()                 // ★ _bucket 노출 → PromQL histogram_quantile() 사용 가능
        .register(registry);
}

public void pay() {
    paymentTimer.record(() -> externalPg.charge());
}
```

**관찰 포인트**:
- count + sum + max + 백분위 (p50 / p95 / p99) 한꺼번에
- **`publishPercentiles` vs `publishPercentileHistogram` (★ 면접 단골)**:
  - `publishPercentiles` — 앱이 직접 계산한 quantile 시계열 (`{quantile="0.95"}`). Prometheus 서버측 집계 불가
  - `publishPercentileHistogram` — `_bucket{le="..."}` 시계열 노출. `histogram_quantile()` PromQL 함수 사용 가능. 다중 인스턴스 집계 OK. **표준**
  - 둘 다 켜도 됨 (학습용 권장)
- `Timer.Sample` 으로 코드 위치 분산 가능
- `@Timed` 어노테이션 — 메서드 자동 측정 (5 주차 AOP 와 같은 메커니즘)

##### 2-4. `@Timed` — AOP 로 자동

```java
@Configuration
public class TimedConfig {
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}

@Service
public class OrderService {
    @Timed("payment_duration_seconds")
    public void pay() {
        externalPg.charge();
    }
}
```

**관찰 포인트**:
- 5 주차 `@Aspect` 와 같은 메커니즘
- self-invocation 함정 — `this.pay()` 호출 시 측정 안 됨 (5, 6, 7, 9, 11 주차 회수)
- `aop.proxy-target-class=true` 가 기본 (Spring Boot 2.0+)


#### ▸ STAGE 3 — Prometheus + Grafana 시각화 (필수)

##### 3-1. docker compose up

```bash
cd topics/12-observability/example
docker compose up -d
docker compose ps
```

##### 3-2. Prometheus 확인

```
http://localhost:9090 → Status → Targets
spring 타겟이 "UP" 인지 확인

쿼리 실행:
- rate(http_server_requests_seconds_count[1m])
- jvm_memory_used_bytes{area="heap"}
- orders_total
```

##### 3-3. Grafana 대시보드

```
http://localhost:3000  (admin / admin)
1. Connections → Data Sources → Prometheus 추가 (URL: http://prometheus:9090)
2. Dashboards → Import → ID = 11378 ("JVM (Micrometer)" 표준 대시보드)
3. Prometheus 데이터소스 선택 → Import

본인 도메인 대시보드 — 직접 패널 추가
```

**관찰 포인트**:
- Spring Boot 표준 대시보드 — JVM / GC / HTTP / DataSource 다 그려짐
- ⚠️ 일부 패널이 "No data" 일 수 있음 — 커뮤니티 대시보드가 특정 메트릭 이름 / 라벨 규칙 가정. Micrometer / Spring Boot 버전 차이로 직접 PromQL 확인 필요
- 본인 도메인 메트릭은 패널 직접 추가


#### ▸ STAGE 4 [살짝] — Trace ID + MDC

##### 4-1. Micrometer Tracing 의존성

```gradle
implementation 'io.micrometer:micrometer-tracing-bridge-brave'
```

##### 4-2. 로그 패턴

```properties
# application.properties
logging.pattern.level=%5p [${spring.application.name:-},%X{traceId:-},%X{spanId:-}]
```

##### 4-3. 동작

```
2026-06-06 ... INFO [app,abc123de456f,789ghi] OrderController : placeOrder start
2026-06-06 ... INFO [app,abc123de456f,789ghi] OrderService : payment ok
2026-06-06 ... INFO [app,abc123de456f,789ghi] OrderController : placeOrder end

→ traceId 로 grep 하면 한 요청의 모든 로그
→ 분산 시스템이면 Zipkin / Tempo 로 시각화
```

**관찰 포인트**:
- traceId 는 **HTTP 요청 컨텍스트 안에서만 채워짐** — 부팅 / `main` / 스케줄러 로그엔 빈 값 (`%X{traceId:-}` 의 기본값)
- 컨트롤러 → 서비스 같은 동기 호출은 같은 traceId 자연
- `@Async` 로 다른 스레드에서는 별도 처리 필요 (6 주차 / 11 주차 함정 회수)


━━━━━━━━━━━━━━━━━━━━━━━━━━
─── 선택 ───
━━━━━━━━━━━━━━━━━━━━━━━━━━

### [선택] ▸ STAGE 5 — 1 ~ 11 주차 회수

본인 도메인의 1 ~ 11 주차 자리에 메트릭 심기. 한 대시보드에 다 그리기.

| 주차 | 메트릭 |
|---|---|
| 3 | `lock_wait_seconds` Timer |
| 5 | `@Timed` 자동 |
| 6 | `events_published_total` Counter / `async_queue_size` Gauge |
| 7 | Hibernate Statistics (Spring 자동) |
| 9 | `cache_hits_total` / `cache_hit_ratio` Gauge |
| 10 | `http_client_duration_seconds` Timer / CB 상태 |
| 11 | `auth_success_total` / `auth_failure_total{reason=...}` |


━━━━━━━━━━━━━━━━━━━━━━━━━━
금지 키워드 — STAGE 1 ~ 2
━━━━━━━━━━━━━━━━━━━━━━━━━━
- OpenTelemetry 깊이 — Tracing 표준이지만 학습 범위 밖
- Datadog / New Relic / Dynatrace — 상용 APM. 본 학습 후
- ELK / Loki — 로그 수집. 본 학습 후
- 알람 / PagerDuty — 운영 영역


━━━━━━━━━━━━━━━━━━━━━━━━━━
학습 기록
━━━━━━━━━━━━━━━━━━━━━━━━━━

### 시리즈 회수 — 1 ~ 11 주차 → 12 주차

```
주차   학습 핵심                        12 주차 메트릭
─────────────────────────────────────────────────────────────
1  JVM 메모리 / 스레드                  → Actuator 자동
2  TX 격리 / 실패율                     → 자작 Timer / Counter
3  분산락 SETNX / Lua                   → lock_wait_seconds Timer
4  IoC Bean / 부팅                      → /actuator/info
5  AOP @Aspect                          → @Timed / TimedAspect
6  Event publishEvent                   → 자작 Counter
7  JPA N+1 / 영속성                     → Hibernate Statistics
8  Index EXPLAIN                        → DB exporter (외부)
9  Cache hitRate                        → cache_* Gauge / Counter
10 HTTP Pool / CB                       → resilience4j actuator 자동
11 Auth 로그인                          → auth_* Counter
12 ★ 한 대시보드에 다 그림
```

### 면접 단골
- **"관측 3 축 차이"** — Metrics 집계 / Logs 시점 / Traces 한 요청 흐름
- **"Counter / Gauge / Timer 차이"** — 누적 / 현재 / 시간 분포
- **"Prometheus pull vs push"** — pull = 발견 / scrape / 서비스 추적 쉬움. push = 짧은 작업 (Pushgateway)
- **"`@Timed` self-invocation"** — 5, 6, 7, 9, 11 주차와 동일 프록시 메커니즘
- **"PromQL rate vs irate"** — rate = window 평균 / irate = 마지막 2 샘플. 보통 rate
- **"histogram_quantile"** — 분포에서 백분위. p95 / p99
- **"Trace ID 의 가치"** — 한 요청의 모든 로그 / 분산 시스템에서 필수
- **"Actuator 엔드포인트 운영 보안"** — 11 주차 회수 — 인증 필수

### 실무 확장 화두
- **★ 메트릭 카디널리티 폭발 (실무 1 위 사고)** — tag 에 **절대로** user_id / order_id / sessionId / request_id 같은 고유값 넣지 말 것. Prometheus 시계열 폭증 → Prometheus 메모리 OOM → 모니터링 시스템 자체 다운. 메트릭은 "집합" 을 위한 것 / "개별 조회" 는 로그 / 트레이스. user_id 별 통계가 필요해도 메트릭이 아니라 로그에서 집계
- **알람 임계치** — p95 응답 시간 / 에러율 / 큐 사이즈 — 자동 알람 (12 주차 범위 밖)
- **APM 상용 도구** — Datadog / New Relic. Micrometer 호환 (registry 교체)
- **로그 수집** — Loki / ELK. 12 주차 범위 밖
- **메트릭 보존 정책** — Prometheus 기본 15 일. 장기 보관은 Thanos / Cortex
- **scrape interval 결정** — 5 s ~ 60 s. 짧을수록 비용. 1 분이 표준
- **메트릭 vs 로그 비용** — 메트릭 = 시계열 (집계, 저렴) / 로그 = 텍스트 (전수, 비쌈)
- **`@Timed` + `@Transactional` + `@PreAuthorize`** — 5 주차 양파 위에 12 주차 + 11 주차. `@Order` 로 명시
- **NTP 시간 동기화** — 분산 환경에서 서버 간 시간이 1 초만 차이 나도 메트릭 왜곡 + 트레이스 정합성 손상. NTP 는 인프라 기초. 로컬 docker 는 OK, 실제 운영은 chronyd / systemd-timesyncd 필수


━━━━━━━━━━━━━━━━━━━━━━━━━━
시리즈 마무리
━━━━━━━━━━━━━━━━━━━━━━━━━━

```
1 ~ 12 주차 완주
─────────────────────────────────────
"잘 만들기" (1 ~ 11) + "잘 동작하는지 알기" (12) = 백엔드 엔지니어
```

축하합니다. 이제 본인 도메인에 1 ~ 12 주차 다 적용해서 시리즈 종합 작품을 만들 차례.


━━━━━━━━━━━━━━━━━━━━━━━━━━
막힐 때
━━━━━━━━━━━━━━━━━━━━━━━━━━

**`/actuator/prometheus` 가 비어있음**:
1. `micrometer-registry-prometheus` 의존성 확인
2. `management.endpoints.web.exposure.include=*` (또는 `prometheus`)
3. 서비스가 메트릭 등록했는가

**Prometheus 가 spring 타겟을 못 찾음**:
1. prometheus.yml 의 target 이 정확한가
2. docker 안에서 호스트 접근 — `host.docker.internal:8080` (macOS / Windows) / `172.17.0.1:8080` (Linux)
3. Spring Boot 가 0.0.0.0 으로 bind 되어 있는가

**Grafana 에 데이터 안 보임**:
1. Prometheus 데이터소스 URL — `http://prometheus:9090` (docker network 안)
2. Time range 가 맞는가 — 기본 Last 5m
3. 쿼리 자체 — Prometheus 에서 먼저 확인

**`@Timed` 가 동작 안 함**:
1. `TimedAspect` 빈 등록했는가
2. self-invocation — 5, 6, 7, 9, 11 주차와 동일
3. 메서드가 `public` 인가

**메트릭 카디널리티 폭발**:
1. tag 에 고유값 (user_id / order_id) 넣지 않기
2. Prometheus `cardinality` 페이지에서 확인
3. 필요하면 로그 / 트레이스로 옮기기
