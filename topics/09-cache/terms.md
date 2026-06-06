# 9주차 캐시 + Spring Cache + Redis — 용어 정리

> 8 주차의 인덱스 용어 정리와 같은 형식.

---

## 🌳 캐시 본질

| 용어 | 풀어쓰면 |
|---|---|
| **캐시** (Cache) | 자주 쓰는 결과를 메모리에 보관. 다시 계산 / 조회 X |
| **hit / miss** | 캐시에서 찾음 / 없어서 원본 조회 |
| **hit ratio** | hit / (hit + miss). 80% 이상이 의미 있는 캐시 |
| **TTL** (Time To Live) | 캐시 항목 유효 기간. 만료 시 삭제 |
| **eviction** | 캐시에서 항목 제거. TTL 만료 / 용량 초과 |
| **LRU** (Least Recently Used) | 가장 오래 안 쓴 것부터 제거. Caffeine 기본 |
| **LFU** (Least Frequently Used) | 가장 적게 쓴 것부터 제거. Caffeine W-TinyLFU |
| **maximumSize** | 캐시 최대 항목 수. 초과 시 eviction |
| **expireAfterWrite** | 작성 후 N 시간 만료 |
| **expireAfterAccess** | 마지막 접근 후 N 시간 만료 |

## 🔄 캐시 패턴

| 용어 | 풀어쓰면 |
|---|---|
| **Cache-Aside** (Lazy Loading) | 가장 흔함. 앱이 캐시 직접 확인 / 갱신. Spring `@Cacheable` 기본 |
| **Read-Through** | 캐시가 직접 DB 읽기. 라이브러리 처리 |
| **Write-Through** | 쓰기 = 캐시 + DB 동시. 일관성 강함, 쓰기 느림 |
| **Write-Behind** (Write-Back) | 캐시 쓰기 → DB 비동기. 빠름, 손실 위험 |
| **negative caching** | null (없음) 도 캐시. DB miss 폭주 방지 |
| **refresh-ahead** | 만료 전 백그라운드 갱신. Caffeine `refreshAfterWrite` |

## 📦 로컬 vs 분산

| 용어 | 풀어쓰면 |
|---|---|
| **로컬 캐시** | JVM 힙. μs 단위. 다중 인스턴스 동기화 X |
| **Caffeine** | Java 로컬 캐시 표준. ConcurrentHashMap + W-TinyLFU + 통계 |
| **EhCache** | 옛 Java 로컬 캐시. Caffeine 으로 거의 대체 |
| **분산 캐시** | 네트워크 너머. ms 단위. 모든 인스턴스 공유 |
| **Redis** | 인메모리 KV 스토어. 분산 캐시 표준. 3 주차에서 분산락으로 사용 |
| **Memcached** | 또 다른 분산 캐시. Redis 보다 단순 |
| **L1 + L2** | 하이브리드 — L1 = Caffeine (빠름) + L2 = Redis (공유) |

## 🎭 Spring Cache 추상

| 용어 | 풀어쓰면 |
|---|---|
| **`@EnableCaching`** | 캐시 활성화. `@SpringBootApplication` 옆 |
| **`@Cacheable`** | 메서드 결과 캐시. 같은 인자 → 캐시 반환 |
| **`@CacheEvict`** | 캐시 무효화. 변경 메서드에 |
| **`@CachePut`** | 캐시에 강제 저장. 메서드는 항상 실행 |
| **`@Caching`** | 여러 어노테이션 묶음 — evict + put 동시 |
| **`value` / `cacheNames`** | 캐시 이름 (그룹). 같은 이름 = 같은 영역 |
| **`key` (SpEL)** | 동적 키. `key = "#userId + ':' + #type"` |
| **`condition` (SpEL)** | 조건부 캐싱. `condition = "#id > 100"` |
| **`unless` (SpEL)** | 조건부 캐싱 안 함. `unless = "#result == null"` |
| **`sync = true`** | 같은 키 동시 miss 시 한 스레드만 실행 (단일 JVM stampede 방지) |
| **`allEntries = true`** | `@CacheEvict` — 캐시 전체 비우기 |
| **`CacheManager`** | 캐시 추상화. 구현체 교체로 Caffeine ↔ Redis 전환 |
| **`ConcurrentMapCacheManager`** | 기본. HashMap 기반. 학습용 |
| **`CaffeineCacheManager`** | Caffeine 기반 |
| **`RedisCacheManager`** | Redis 기반 |

## ⚠️ 캐시 함정

| 용어 | 풀어쓰면 |
|---|---|
| **Stale read** | DB 변경 후 캐시 안 비움 → 옛 값 반환 |
| **Cache stampede** | TTL 만료 시 동시 miss → DB / 외부 API 폭주 |
| **thundering herd** | stampede 의 다른 이름 |
| **jitter** | TTL 에 ±20% 무작위. 동시 만료 회피 |
| **Cache penetration** | 존재하지 않는 키 반복 조회 → 매번 DB miss. negative caching 으로 해결 |
| **Cache avalanche** | 여러 키 동시 만료. jitter / 다단 캐시 |
| **무한 캐시 OOM** | maximumSize / TTL 없으면 메모리 누수 → OOM |
| **self-invocation** | 5, 6, 7 주차 회수. `this.cached()` 호출은 프록시 우회 → 캐시 X |

## 🔑 키 설계 + SpEL

| 용어 | 풀어쓰면 |
|---|---|
| **SpEL** (Spring Expression Language) | `#userId` / `#result.id` / `#root.method.name` 등 |
| **`#param`** | 메서드 파라미터. `key = "#userId"` |
| **`#result`** | 메서드 반환값. `@CachePut(key = "#result.id")` |
| **`#root`** | 호출 컨텍스트. `#root.method.name` / `#root.args` |
| **복합 키** | `key = "#userId + ':' + #type"` |
| **KeyGenerator** | 키 생성 전략. SpEL 안 쓰고 인자 → 키 자동 |
| **5 주차 회수** | `@DistributedLock(key = SpEL)` 과 같은 메커니즘. -parameters 권장 |

## 💾 Redis 통합

| 용어 | 풀어쓰면 |
|---|---|
| **`spring-boot-starter-data-redis`** | Spring Data Redis 묶음 |
| **Lettuce** | Spring Boot 2.x+ 기본 Redis 클라이언트. 3 주차에서 사용 |
| **`RedisConnectionFactory`** | 연결 팩토리. Lettuce / Jedis |
| **`RedisCacheConfiguration`** | TTL / 직렬화 / prefix 설정 |
| **`StringRedisSerializer`** | 키 직렬화 — 사람이 읽기 좋음 |
| **`GenericJackson2JsonRedisSerializer`** | 값 직렬화 — JSON. 표준 |
| **`JdkSerializationRedisSerializer`** | 옛 방식. JVM 의존 — 권장 X |
| **`maxmemory + allkeys-lru`** | Redis 메모리 제한 + LRU eviction. 운영 필수 |
| **`redis-cli`** | Redis CLI. `keys *` / `get key` / `ttl key` / `flushdb` |

## 🌟 7 주차 / 8 주차 회수

| 용어 | 풀어쓰면 |
|---|---|
| **JPA 1 차 캐시** | 7 주차. 영속성 컨텍스트. 트랜잭션 내만 |
| **JPA 2 차 캐시** | EntityManagerFactory 레벨. Hibernate `@Cache` |
| **Spring Cache vs 2 차 캐시** | 메서드 vs Entity. 실무는 Spring Cache 일반 |
| **인덱스 vs 캐시** | 쿼리 빠르게 vs 쿼리 자체 차단. 직교. 같이 씀 |

## 🧱 학습 도구

| 용어 | 풀어쓰면 |
|---|---|
| **`com.github.ben-manes.caffeine:caffeine`** | Caffeine 의존성 |
| **`spring-boot-starter-cache`** | Spring Cache 추상 |
| **`spring-boot-starter-data-redis`** | Redis + Lettuce |
| **`recordStats()`** | Caffeine 통계 활성화 |
| **`stats()`** | Caffeine 통계 조회 — hitRate / evictionCount 등 |
| **Micrometer Cache Metrics** | 캐시 지표 노출. 12 주차 (관측) 연결 |

---

## ★ STAGE 1 진입 관문 (입으로 답)

1. **Cache-Aside 4 단계** — 캐시 확인 → hit 반환 / miss DB → 캐시 저장 → 반환
2. **로컬 vs 분산 트레이드오프** — Caffeine (μs, JVM 별) / Redis (ms, 공유)
3. **Cache stampede + 해결 3** — TTL 만료 동시 miss → DB 폭주. jitter / 분산락 / refresh-ahead

## ★ STAGE 2 진입 관문 (9 주차 가장 중요)

1. **`@Cacheable` / `@CacheEvict` / `@CachePut` 차이** — 캐시 반환 / 비움 / 즉시 갱신
2. **`@Cacheable` self-invocation** — 5, 6, 7 주차 회수. `this` 우회 함정
3. **CacheManager 교체** — 코드 안 바꾸고 Caffeine ↔ Redis
