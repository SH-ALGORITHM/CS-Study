# 12주차 Observability + Micrometer + Prometheus + Grafana — 용어 정리

> 11 주차와 같은 형식. 시리즈 마지막.

---

## 🔍 관측 본질

| 용어 | 풀어쓰면 |
|---|---|
| **Observability** | 외부에서 시스템 내부 상태를 추론할 수 있는 정도 |
| **3 Pillars** | Metrics + Logs + Traces |
| **Metrics** | 수치 시계열. 집계 / 알람 |
| **Logs** | 시점별 텍스트. 디버깅 / 감사 |
| **Traces** | 한 요청의 분산 흐름. span / traceId |
| **APM** (Application Performance Monitoring) | 성능 관측 통합 도구. Datadog / New Relic 등 |
| **SLI / SLO / SLA** | Service Level Indicator / Objective / Agreement |

## 🪛 Spring Boot Actuator

| 용어 | 풀어쓰면 |
|---|---|
| **`spring-boot-starter-actuator`** | 관리 엔드포인트 묶음 |
| **`/actuator/health`** | 헬스 체크. UP / DOWN |
| **`/actuator/info`** | 버전 / 빌드 정보 |
| **`/actuator/metrics`** | 사용 가능한 메트릭 목록 |
| **`/actuator/prometheus`** | Prometheus scrape 엔드포인트 |
| **`/actuator/loggers`** | 로그 레벨 런타임 변경 |
| **`management.endpoints.web.exposure.include`** | 노출할 엔드포인트 (`*` / `health,info` 등) |
| **운영 보안** | 11 주차 회수 — Actuator 는 Spring Security 로 인증 필수 |

## 📏 Micrometer

| 용어 | 풀어쓰면 |
|---|---|
| **Micrometer** | JVM 메트릭 facade. SLF4J 의 메트릭 버전 |
| **`MeterRegistry`** | 메트릭 등록소. 백엔드 (Prometheus / Datadog) 별 구현체 |
| **`PrometheusMeterRegistry`** | Prometheus 백엔드 |
| **`Counter`** | 누적 (단조 증가). `orders_total` |
| **`Gauge`** | 현재 값 (증감 OK). `queue_size` |
| **`Timer`** | 시간 분포. count + sum + max + 백분위 |
| **`publishPercentiles`** | 앱 계산 quantile. `{quantile="0.95"}` 시계열. 다중 인스턴스 집계 불가 |
| **`publishPercentileHistogram`** | `_bucket{le="..."}` 시계열. PromQL `histogram_quantile()` 사용 가능. 다중 집계 OK (표준) |
| **`DistributionSummary`** | 일반 분포 |
| **`LongTaskTimer`** | 진행 중 작업 측정 (오래 걸리는 작업) |
| **`@Timed`** | 메서드 자동 측정. `TimedAspect` 빈 필요 |
| **`@Counted`** | 메서드 호출 카운터 |
| **tag (label)** | 메트릭 분리 차원. `status=200` / `method=GET` |
| **카디널리티 폭발** | tag 에 고유값 (user_id) 넣어 시계열 폭증. 금기 |

## 🔥 Prometheus

| 용어 | 풀어쓰면 |
|---|---|
| **Prometheus** | 시계열 DB. pull 모델 |
| **scrape** | Prometheus 가 `/actuator/prometheus` 끌어옴 (5 ~ 60 초 간격) |
| **`prometheus.yml`** | scrape 설정 |
| **`job_name`** | scrape 그룹 |
| **`targets`** | 끌어올 대상 |
| **PromQL** | Prometheus 쿼리 언어 |
| **`rate(metric[1m])`** | 1 분 window 평균 (Counter 의 초당 비율) |
| **`irate(metric[1m])`** | 마지막 2 샘플 (즉시) |
| **`sum by (label)`** | 라벨 기준 합산 |
| **`histogram_quantile(0.95, ...)`** | 분포에서 95 백분위 |
| **`up`** | 타겟 상태 (1 = 정상 / 0 = 다운) |
| **Pushgateway** | 짧은 작업 (batch) 메트릭 push. 일반 서비스는 pull |
| **보존 정책** | 기본 15 일. 장기 = Thanos / Cortex |

## 📊 Grafana

| 용어 | 풀어쓰면 |
|---|---|
| **Grafana** | 시각화 도구. 다중 데이터소스 |
| **데이터소스** | Prometheus / Loki / Tempo / MySQL 등 |
| **대시보드** | 패널 모음 |
| **패널** | 한 그래프 / 통계 |
| **대시보드 ID** | grafana.com/dashboards 의 공개 대시보드 번호 |
| **JVM (Micrometer) 11378** | Spring Boot 표준 대시보드 ID |
| **변수** (variable) | 대시보드 동적 필터 — `$instance` / `$app` |
| **알람** | 패널 단위 임계치 알림 |

## 🔍 Trace ID / Logs / MDC

| 용어 | 풀어쓰면 |
|---|---|
| **traceId** | 한 요청 전체의 고유 ID |
| **spanId** | 한 단위 작업의 ID. trace 안 |
| **MDC** (Mapped Diagnostic Context) | SLF4J 의 ThreadLocal map |
| **Micrometer Tracing** | Spring Boot 3+ 의 표준. 옛 Spring Cloud Sleuth 후속 |
| **Brave** | Zipkin 호환 tracer |
| **OpenTelemetry** | CNCF 표준 tracer |
| **`%X{traceId}`** | logback 로그 패턴에 MDC 값 |
| **Zipkin** | trace 수집 / 시각화 도구 |
| **Tempo** | Grafana 의 trace 백엔드 |
| **분산 추적** | 마이크로서비스 간 traceId 전파 (HTTP header `traceparent`) |

## 🌟 1 ~ 11 주차 회수

| 주차 | 메트릭 자리 |
|---|---|
| **1 JVM** | jvm.* 자동 노출 (heap / threads / GC) |
| **2 TX** | 자작 Timer / Counter (실패율) |
| **3 Lock** | lock_wait_seconds Timer / lock_acquire_total Counter |
| **4 IoC** | /actuator/info — 부팅 시간 / Bean 개수 |
| **5 AOP** | @Timed / TimedAspect (5 주차 와 같은 메커니즘) |
| **6 Event** | events_published_total Counter |
| **7 JPA** | Hibernate Statistics (자동) |
| **8 Index** | DB exporter (pg_exporter — 외부) |
| **9 Cache** | cache_hits_total / cache_hit_ratio Gauge |
| **10 HTTP** | resilience4j actuator 자동 |
| **11 Auth** | auth_success_total / auth_failure_total Counter |

## 🧱 학습 도구

| 용어 | 풀어쓰면 |
|---|---|
| **`spring-boot-starter-actuator`** | Actuator |
| **`micrometer-registry-prometheus`** | Prometheus 백엔드 |
| **`micrometer-tracing-bridge-brave`** | Brave (Zipkin 호환) |
| **`micrometer-tracing-bridge-otel`** | OpenTelemetry |
| **docker compose** | Prometheus + Grafana 한 번에 |

---

## ★ STAGE 1 진입 관문

1. 관측 3 축 — Metrics / Logs / Traces 각각 언제
2. Counter / Gauge / Timer / DistributionSummary 차이
3. Prometheus pull 모델 + scrape 의 의미

## ★ STAGE 2 진입 관문

1. tag 카디널리티 폭발 — 어떤 값을 tag 로 / 어떤 값을 절대 X
2. `@Timed` self-invocation — 5, 6, 7, 9, 11 주차와 같은 메커니즘
3. PromQL rate / sum by / histogram_quantile 본인 예
