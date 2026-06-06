# 10주차 HTTP / Connection Pool / Circuit Breaker — 용어 정리

> 9 주차 캐시 용어 정리와 같은 형식.

---

## 🌐 HTTP 클라이언트

| 용어 | 풀어쓰면 |
|---|---|
| **RestTemplate** | Spring 의 옛 동기 HTTP 클라이언트. 유지보수 모드 |
| **WebClient** | Spring 5+ Reactive (Reactor). 논블로킹 / 비동기 |
| **RestClient** | Spring 6.1+ 권장. RestTemplate 후속. fluent API |
| **HttpClient (Java 11+)** | JDK 표준 HTTP 클라이언트. `JdkClientHttpRequestFactory` 명시해야 RestClient 가 사용. **자동 탐지 체인엔 없음** |
| **RestClient 자동 탐지 순서** | Apache HttpComponents (httpclient5) → Jetty → SimpleClientHttpRequestFactory. 클래스패스로 결정. JDK HttpClient 는 명시 필요 |
| **Apache HttpClient 5** | 가장 흔한 외부 라이브러리. PoolingConnectionManager |
| **OkHttp** | Square 의 HTTP 클라이언트. Android / 일부 서버 |
| **Spring Cloud OpenFeign** | 인터페이스 기반 선언형 클라이언트. RestClient 추상화 |
| **`requestFactory`** | RestTemplate / RestClient 가 사용할 실제 HTTP 클라이언트 |

## 🔗 TCP / Keep-Alive

| 용어 | 풀어쓰면 |
|---|---|
| **3-way handshake** | TCP 연결 — SYN → SYN-ACK → ACK. RTT 1 회 |
| **TLS handshake** | HTTPS — TCP 위 추가 1 ~ 2 RTT. 매번 비싸므로 재사용 필수 |
| **Keep-Alive** | HTTP 1.1 기본. 한 TCP 연결로 여러 요청 |
| **`Connection: close`** | Keep-Alive 끄기. 매번 새 연결 |
| **idle connection** | 응답 받고 풀에 반환된 유휴 연결 |
| **HTTP/2 multiplexing** | 한 TCP 연결로 여러 요청 동시. Pool 의미 줄어듦 |

## 🏊 Connection Pool

| 용어 | 풀어쓰면 |
|---|---|
| **Connection Pool** | TCP 연결 재사용 풀 |
| **PoolingHttpClientConnectionManager** | Apache HttpClient 5 의 풀 관리자 |
| **maxTotal** | 풀 전체 연결 한도 |
| **maxPerRoute** | 호스트별 (호스트 + 포트) 연결 한도. 기본 2 (너무 작음) |
| **route** | 한 호스트로의 연결 경로 |
| **Connection TTL** | 유휴 연결 만료 시간. 너무 길면 서버 끊기 → NoHttpResponseException |
| **`evictIdleConnections`** | 유휴 연결 정리 |
| **`validateAfterInactivity`** | 풀에서 꺼낼 때 유효성 검증. 비용 있지만 NoHttpResponseException 방지 |
| **Pool 고갈** | 모든 연결 사용 중 → 새 요청 대기 (connection request timeout) |

## ⏱ Timeout

| 용어 | 풀어쓰면 |
|---|---|
| **connect timeout** | TCP 소켓 연결 시간. 방화벽 / DNS / 외부 다운 시 발동. 1 ~ 3 초 |
| **read timeout** (socket timeout) | 응답 데이터 대기. 도메인 SLA 기준 (1 ~ 30 초) |
| **request timeout** (전체) | connect + read 의 상한. WebClient / RestClient 일부 지원 |
| **connection request timeout** | 풀에서 연결 꺼내기 대기 시간 |
| **무한 대기** | timeout 미설정 — 실무 장애 1 위 |
| **timeout 비대칭** | 클라이언트 timeout < 서버 timeout 이어야. 반대면 좀비 응답 |

## 🛡 Resilience4j

| 용어 | 풀어쓰면 |
|---|---|
| **Resilience4j** | Java 표준 회복성 라이브러리. Spring Boot 통합 |
| **Hystrix** | Netflix 의 옛 라이브러리. deprecated |
| **CircuitBreaker** | 장애 감지 → 빠른 실패 → 자동 복구 |
| **CLOSED** | 정상 상태. 외부 호출 그대로 |
| **OPEN** | 임계치 초과 → 외부 호출 자체 안 함. fallback 즉시 |
| **HALF_OPEN** | OPEN 시간 후 시도. 성공 = CLOSED / 실패 = OPEN |
| **sliding-window-size** | 통계 윈도우 크기. 최근 N 회 측정 |
| **failure-rate-threshold** | 실패율 임계치 (%). 초과 시 OPEN |
| **wait-duration-in-open-state** | OPEN 유지 시간. 이후 HALF_OPEN |
| **slow-call-rate-threshold** | 느린 호출 비율 임계치 (slow 도 실패로 카운트) |
| **fallbackMethod** | OPEN 시 호출되는 메서드. 같은 시그니처 + Throwable |

## 🔁 Retry

| 용어 | 풀어쓰면 |
|---|---|
| **`@Retry`** | 일시 장애 자동 재시도 |
| **max-attempts** | 최대 시도 횟수 (포함) |
| **wait-duration** | 재시도 간격 |
| **exponential-backoff** | 간격이 지수 증가. 500ms → 1s → 2s |
| **jitter** | backoff 에 무작위 추가. thundering herd 방지 |
| **retryExceptions** | 어떤 예외만 retry. IOException / 5xx 등 |
| **idempotency** | 같은 요청 N 회 = 같은 결과. Retry 필수 조건 |
| **idempotency-key** | 결제 등에서 중복 방지 헤더 |

## 🚧 Bulkhead

| 용어 | 풀어쓰면 |
|---|---|
| **Bulkhead** | 외부 호출 전용 스레드 풀 분리. 톰캣 워커 보호 |
| **THREADPOOL** Bulkhead | 별도 스레드 풀로 격리 |
| **SEMAPHORE** Bulkhead | 동시 호출 수 제한만 (스레드 분리 없음) |
| **max-thread-pool-size** | 외부 호출 풀 최대 |
| **core-thread-pool-size** | 외부 호출 풀 기본 |

## ⚠️ 실무 함정

| 용어 | 풀어쓰면 |
|---|---|
| **NoHttpResponseException** | 서버가 idle 연결 끊었는데 클라이언트는 모름. validateAfterInactivity / 짧은 TTL 로 방지 |
| **무한 대기 장애** | timeout 미설정. 외부 다운 = 내 다운 |
| **Pool 고갈 → connection request timeout** | maxTotal 부족 / release 누락 |
| **Pool 누수** | 응답 후 connection close 안 함. try-with-resources 필수 |
| **5xx Retry 함정** | 500 = 재시도 안 함 (영구) / 503 = 재시도 OK (일시) |
| **idempotency 위반** | Retry 시 같은 결제 2 회 — idempotency-key 필수 |
| **OSIV + 외부 호출** | 7 주차 회수. OSIV ON 상태에서 외부 호출 = DB 커넥션 + 톰캣 워커 둘 다 점유 |

## 🌟 9 주차 / 12 주차 결합

| 용어 | 풀어쓰면 |
|---|---|
| **캐시 + Circuit Breaker** | 평소 캐시 hit 빠르게 / 외부 장애 시 fallback |
| **stale-while-revalidate** | 캐시 만료 시 옛 값 반환 + 백그라운드 갱신 |
| **Micrometer Cache Metrics** | 12 주차 (관측) — Pool / Circuit Breaker 메트릭 자동 노출 |
| **Prometheus + Grafana** | 12 주차 본론 |

## 🧱 학습 도구

| 용어 | 풀어쓰면 |
|---|---|
| **`spring-boot-starter-web`** | Spring MVC + Tomcat |
| **`spring-boot-starter-webflux`** | WebClient + Reactor |
| **`org.apache.httpcomponents.client5:httpclient5`** | Apache HttpClient 5 |
| **`resilience4j-spring-boot3`** | Resilience4j Spring 통합 |
| **`server.tomcat.threads.max`** | 톰캣 worker 최대 수 |
| **`server.tomcat.accept-count`** | 대기 큐 크기 |

---

## ★ STAGE 1 진입 관문

1. 외부 API 지연이 톰캣 워커를 점유하는 메커니즘 — 1 분 본인 말로
2. Timeout 3 종 + 미설정 시 위험
3. Circuit Breaker 3 상태 + 각 상태 외부 호출 여부

## ★ STAGE 2 진입 관문

1. RestTemplate / WebClient / RestClient 차이 + 본인 선택
2. Connection Pool 의 maxTotal / maxPerRoute 결정
3. NoHttpResponseException 발생 조건 + 방지
