# 9주차 예시 코드 — 상품 카탈로그 (Spring Cache + Caffeine + Redis)

scenario.md 의 12 개 도메인과 **별개로** 만든 참고 코드입니다. 7 주차 example 의 구조 (domain + infra + stage) 를 그대로 연장.

> ⚠️ **베끼지 마시고 본인 도메인으로 변환하세요.**

## 8 주차와 무엇이 같고 다른가

| | 8 주차 Index | 9 주차 Cache |
|---|---|---|
| 풀려는 문제 | SQL 한 회 빨라지게 | SQL 자체 차단 |
| 도구 | EXPLAIN + CREATE INDEX | `@Cacheable` + Caffeine + Redis |
| 적용 위치 | DB | 애플리케이션 메모리 + 네트워크 캐시 |
| 본질 | 옵티마이저 plan | 캐시 HIT / MISS |
| 면접 직결 | 인덱스 미사용 6 / 복합 / 커버링 | Cache-Aside / stampede / self-invocation |

## 폴더 구조

```
example/
├── README.md
├── build.gradle             Spring Boot 3.2 + JPA + Cache + Caffeine + Redis
├── docker-compose.yml       Redis 7 (Stage2_4 부터 사용)
├── src/main/
│   ├── java/
│   │   ├── domain/
│   │   │   ├── Product.java
│   │   │   └── ProductRepository.java
│   │   ├── infra/
│   │   │   ├── MeasurementLog.java
│   │   │   ├── JpaConfig.java        @EnableJpaRepositories
│   │   │   └── Seeder.java           Product seed
│   │   └── stage/
│   │       ├── s1/  Cache-Aside 손 작성
│   │       │   ├── Stage1_1_NoCache.java        — 매 요청 DB
│   │       │   ├── Stage1_2_HandMadeCache.java  — ConcurrentHashMap 손 캐시 (무한 증가)
│   │       │   └── Stage1_3_Caffeine.java       — maximumSize + LRU + stats
│   │       ├── s2/  Spring Cache + Redis ★
│   │       │   ├── Stage2_1_Cacheable.java      — @Cacheable 한 줄
│   │       │   ├── Stage2_2_CacheEvict.java     — 변경 시 무효화
│   │       │   ├── Stage2_3_CachePut.java       — 즉시 갱신
│   │       │   ├── Stage2_4_RedisManager.java   — CacheManager 만 교체
│   │       │   └── Stage2_5_SelfInvocation.java — 5,6,7 주차 회수
│   │       ├── s3/  일관성 함정
│   │       │   ├── Stage3_1_StaleRead.java      — DB 변경 + 캐시 안 비움
│   │       │   └── Stage3_2_CacheStampede.java  — sync=true 해결
│   │       └── s4/  로컬 vs 분산
│   │           └── Stage4_1_LocalVsDist.java    — Caffeine vs Redis 측정
│   └── resources/
│       └── application.properties   H2 + Redis 호스트
```

## 실행 방법

### 1. Redis 띄우기 (Stage2_4 / Stage3_2 / Stage4_1 만)

```bash
cd topics/09-cache/example
docker compose up -d
docker compose ps
```

Stage1_1 ~ Stage2_3 / Stage3_1 / Stage2_5 는 Redis 불필요 — H2 + Caffeine 만.

### 2. 실행

```bash
# STAGE 1
./gradlew run -PmainClass=stage.s1.Stage1_1_NoCache
./gradlew run -PmainClass=stage.s1.Stage1_2_HandMadeCache
./gradlew run -PmainClass=stage.s1.Stage1_3_Caffeine

# STAGE 2
./gradlew run -PmainClass=stage.s2.Stage2_1_Cacheable
./gradlew run -PmainClass=stage.s2.Stage2_2_CacheEvict
./gradlew run -PmainClass=stage.s2.Stage2_3_CachePut
./gradlew run -PmainClass=stage.s2.Stage2_4_RedisManager       # Redis 필요
./gradlew run -PmainClass=stage.s2.Stage2_5_SelfInvocation

# STAGE 3
./gradlew run -PmainClass=stage.s3.Stage3_1_StaleRead
./gradlew run -PmainClass=stage.s3.Stage3_2_CacheStampede

# STAGE 4
./gradlew run -PmainClass=stage.s4.Stage4_1_LocalVsDist        # Redis 필요
```

### 3. Redis 확인 (Stage2_4 후)

```bash
docker exec -it cs-study-09-redis redis-cli
> keys *
> get products::1
> ttl products::1
```

## 핵심 학습 흐름

1. **STAGE 1** — 손 캐시 (HashMap) → Caffeine 의 3 단계 진화. eviction / TTL / stats 의 필요성 직접 체감
2. **STAGE 2** ★ — `@Cacheable` 한 줄 추출 + `@CacheEvict` / `@CachePut` / Redis 교체 + self-invocation 함정 (5, 6, 7 주차 회수). **9 주차 가장 중요한 학습**
3. **STAGE 3** — Stale read + Cache stampede 함정 직접 재현
4. **STAGE 4** — 로컬 (μs) vs 분산 (ms) 측정 + 결정 매트릭스
