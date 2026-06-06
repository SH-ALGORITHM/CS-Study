# 12주차 예시 코드 — Actuator + Micrometer + Prometheus + Grafana

> ⚠️ 본인 도메인에 메트릭 심기 위한 참고.

## 11 주차와 다른 점

| | 11 주차 Auth | 12 주차 관측 |
|---|---|---|
| 문제 | 매 요청 신원 확인 | 시스템이 지금 어떻게 동작하나 |
| 도구 | Spring Security / JWT / @PreAuthorize | Actuator / Micrometer / Prometheus / Grafana |
| 회수 | 5,6,7,9,10 주차 | **1 ~ 11 주차 매 주차 메트릭 자리** |

## 폴더 구조

```
example/
├── README.md
├── build.gradle             Actuator + Micrometer Prometheus + Brave Tracing
├── docker-compose.yml       Prometheus + Grafana
├── prometheus.yml           scrape 설정
└── src/main/
    ├── resources/application.properties
    └── java/
        ├── infra/MeasurementLog
        └── app/
            ├── AppServer       메인 서버 (:8080) — /api/order /api/timed
            ├── OrderService    Counter / Gauge / Timer / @Timed 시연
            └── LoadGenerator   부하 생성기 (20 RPS / 5% 실패)
```

## 실행 방법

### 1. AppServer 띄움

```bash
cd topics/12-observability/example
./gradlew run -PmainClass=app.AppServer
```

```bash
# 확인
curl http://localhost:8080/api/order
# "ok"

curl http://localhost:8080/actuator/health
# {"status":"UP", ...}

curl http://localhost:8080/actuator/prometheus | head -20
# # HELP jvm_memory_used_bytes ...
# # TYPE jvm_memory_used_bytes gauge
# jvm_memory_used_bytes{area="heap",...} 1.234E8
```

### 2. (선택) 부하 생성기

```bash
# 다른 터미널
./gradlew run -PmainClass=app.LoadGenerator
# 20 RPS / 5% 실패율
```

### 3. Prometheus + Grafana 띄움 (STAGE 3)

```bash
docker compose up -d

# Prometheus  http://localhost:9090
#   Status → Targets — "spring" UP 확인
#   Query — rate(orders_total[1m])

# Grafana     http://localhost:3000  (admin / admin)
#   1. Connections → Data Sources → Add → Prometheus
#      URL: http://prometheus:9090
#   2. Dashboards → New → Import
#      ID: 11378  (JVM Micrometer 표준 대시보드)
#      Prometheus 데이터소스 선택 → Import
#      ⚠️ 일부 패널이 "No data" 일 수 있음 — Micrometer / Spring Boot 버전 차이로
#         메트릭 이름 규칙이 어긋날 때. PromQL 로 직접 확인
#   3. 본인 도메인 메트릭은 패널 직접 추가
```

### 4. 핵심 PromQL 쿼리

```promql
# 초당 주문 수
rate(orders_total[1m])

# 결과별 분리
sum by (result) (rate(orders_total[1m]))

# 현재 활성 주문
active_orders

# 응답 시간 p95
histogram_quantile(0.95, rate(order_duration_seconds_bucket[1m]))

# Tomcat 워커 사용량
tomcat_threads_busy_threads
```

## 핵심 학습 흐름

1. **STAGE 1** — Actuator 기본. `/actuator/health` / `/metrics` / `/prometheus` 확인
2. **STAGE 2** ★ — `OrderService` 의 Counter / Gauge / Timer / `@Timed` 코드 읽기
3. **STAGE 3** — docker compose 띄우고 Grafana 에서 시각화
4. **STAGE 4** — 로그의 `traceId` / `spanId` (`application.properties` 의 logging.pattern 적용)
