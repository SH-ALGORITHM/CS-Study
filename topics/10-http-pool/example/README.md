# 10주차 예시 코드 — SlowApiServer + 클라이언트 (HTTP / Pool / Timeout / Circuit Breaker)

> ⚠️ 베끼지 말고 본인 도메인으로 변환.

## 9 주차와 무엇이 다른가

| | 9 주차 캐시 | 10 주차 HTTP / Pool |
|---|---|---|
| 풀려는 문제 | DB 자체 차단 | 외부 호출 부하 제어 |
| 도구 | @Cacheable / Caffeine / Redis | RestClient / Pool / Timeout / Resilience4j |
| 학습 본질 | 같은 결과 = 메모리 반환 | 외부 지연 = 톰캣 워커 점유 |
| 면접 직결 | self-invocation / stampede | timeout 3 종 / Circuit Breaker 3 상태 |

## 폴더 구조

```
example/
├── README.md
├── build.gradle           Spring Boot 3.2 + httpclient5 + resilience4j
└── src/main/
    ├── resources/
    │   └── application.properties     Resilience4j 설정
    └── java/
        ├── infra/MeasurementLog.java
        ├── server/
        │   └── SlowApiServer.java       :8081 — /slow / /fast / /flaky
        └── stage/
            ├── s1/  HTTP 클라이언트 3 종
            │   ├── Stage1_1_RestTemplate
            │   ├── Stage1_2_RestClient (Spring 6.1+)
            │   └── Stage1_3_ApacheHttpClient (Pool 명시)
            ├── s2/  Timeout + 스레드 풀 고갈 ★
            │   ├── Stage2_1_TimeoutMissing
            │   ├── Stage2_2_TimeoutSet
            │   └── Stage2_3_PoolExhaustion (worker 10 + 50 동시)
            └── s4/  Circuit Breaker
                └── Stage4_1_CircuitBreaker (CLOSED → OPEN → HALF_OPEN)
```

STAGE 3 (Connection Pool 본격) 은 시나리오에서만 다룸 (학습 시간 절약).

## 실행 방법

```bash
cd topics/10-http-pool/example

# 1. SlowApiServer 먼저 띄움 (별도 터미널)
./gradlew run -PmainClass=server.SlowApiServer

# 2. 다른 터미널에서 stage 실행
./gradlew run -PmainClass=stage.s1.Stage1_1_RestTemplate
./gradlew run -PmainClass=stage.s1.Stage1_2_RestClient
./gradlew run -PmainClass=stage.s1.Stage1_3_ApacheHttpClient

./gradlew run -PmainClass=stage.s2.Stage2_1_TimeoutMissing      # 5 초 대기
./gradlew run -PmainClass=stage.s2.Stage2_2_TimeoutSet          # 3 초 후 실패
./gradlew run -PmainClass=stage.s2.Stage2_3_PoolExhaustion      # 50 동시

./gradlew run -PmainClass=stage.s4.Stage4_1_CircuitBreaker      # CB 전환
```

## 핵심 학습 흐름

1. **STAGE 1** — RestTemplate / RestClient / Apache HttpClient 비교
2. **STAGE 2 ★** — Timeout 미설정 vs 설정 + 스레드 풀 고갈 시뮬레이션. **10주차 가장 중요**
3. **STAGE 4** — Resilience4j Circuit Breaker. CLOSED → OPEN → HALF_OPEN 전환 직접 관찰
