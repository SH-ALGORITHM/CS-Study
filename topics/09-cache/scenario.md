# 9주차 — DB 자체를 안 가게 만들기 (캐시 + Spring Cache + Redis)

이번 주제: 8 주차에 인덱스로 SQL 한 회당 시간은 ms 단위로 줄였다. 그런데 **같은 쿼리가 초당 수천 회 들어오면** 인덱스로 빨라도 DB 부하 누적 + 커넥션 풀 압박. 결과가 자주 안 바뀐다면 DB 갈 필요 자체가 없다. 9 주차는 메모리에 답을 보관하고 (`@Cacheable` 한 줄) DB 호출을 차단한다. 그 대가는 **캐시 일관성** — 변경 누락 / Stale read / Cache stampede 같은 함정.

5 가지 학습 축:
- **Cache-Aside 패턴** — Spring Cache 기본. miss → DB → 캐시 저장 → 반환 / hit → 캐시 반환
- **로컬 vs 분산** — Caffeine (JVM 안, 빠름, 동기화 X) vs Redis (네트워크, 다중 인스턴스 공유)
- **Spring Cache 추상** ★ — `@Cacheable` / `@CacheEvict` / `@CachePut`. 5 주차 AOP 같은 메커니즘 → self-invocation 함정 회수
- **캐시 일관성 함정** — Stale read / Cache stampede (TTL 만료 시 동시 miss → DB 폭주) / 무한 캐시 OOM / TTL + jitter 전략
- **캐시 키 + SpEL** — `@Cacheable(key = "#userId")` — 5 주차 `@DistributedLock` 과 같은 SpEL 메커니즘

---

## 우선 알아둬야 할 단어 (시작 전 1 분)

| 단어 | 풀어쓰면 |
|---|---|
| **캐시** (Cache) | 자주 쓰는 결과를 메모리에 보관. 다시 계산 / 조회 안 함 |
| **hit / miss** | 캐시에서 찾음 (hit) / 없어서 원본 조회 (miss) |
| **TTL** (Time To Live) | 캐시 항목의 유효 기간. 만료되면 삭제 |
| **eviction** | 캐시에서 항목 제거. TTL 만료 / 용량 초과 시 |
| **Cache-Aside** | 가장 흔한 패턴. 애플리케이션이 캐시 직접 확인 / 갱신 |
| **Read-Through / Write-Through** | 캐시가 직접 DB 읽기 / 쓰기. 라이브러리 / 인프라가 처리 |
| **Caffeine** | Java 로컬 캐시 라이브러리. ConcurrentHashMap + LRU + 통계 |
| **Redis** | 인메모리 KV 스토어. 분산 캐시 표준. 3 주차에서 분산락으로 사용 |
| **Spring Cache** | `@Cacheable` 추상화. CacheManager 구현체 (Caffeine / Redis 등) 교체 가능 |
| **`@Cacheable`** | 메서드 결과를 캐시. 같은 인자 → 캐시 반환 |
| **`@CacheEvict`** | 캐시 무효화. 변경 메서드에 |
| **`@CachePut`** | 캐시에 강제 저장. 메서드는 항상 실행 |
| **Stale read** | DB 변경됐는데 캐시 안 비워 옛 값 반환 |
| **Cache stampede** | TTL 만료 시점 동시 miss → DB 폭주. 대규모 장애 원인 |
| **SpEL key** | `@Cacheable(key = "#userId + ':' + #type")` — 캐시 키 동적 생성 |

> 📚 더 깊은 용어 (eviction 정책 / LRU / LFU / Redis 자료구조 / 2 차 캐시 등) — [`terms.md`](terms.md) 참고.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 0. 개념 숙지 (도메인 무관, 모두 동일)
━━━━━━━━━━━━━━━━━━━━━━━━━━

## 필수 개념 (AI 와 대화하며 숙지)

### 8 주차 → 9 주차 연결
1. **인덱스가 못 푸는 것** — 한 회당 SQL 빨라도 같은 결과 초당 수천 회 = 부하 누적. 결과가 자주 안 바뀌는데 매번 DB 가는 게 낭비
2. **캐시 = DB 자체 차단** — 메모리에 답 보관. 같은 입력 → 즉시 반환 (μs 단위)

### Cache-Aside 패턴
3. **흐름** — (1) 캐시 확인 → (2) hit 이면 반환 / miss 면 DB 조회 → (3) 결과를 캐시에 저장 → (4) 반환
4. **흔한 이유** — 애플리케이션이 캐시 / DB 분리 관리. 캐시 없어도 동작 (degraded)
5. **변경 처리** — 변경 메서드에서 캐시 명시 invalidate (`@CacheEvict`). 또는 짧은 TTL 로 자연 만료

### 로컬 vs 분산
6. **로컬 캐시** (Caffeine) — JVM 힙. μs 단위. 단 다중 인스턴스 동기화 X (각 JVM 별 캐시)
7. **분산 캐시** (Redis) — 네트워크 경유. ms 단위. 모든 인스턴스 공유. 단 Redis 장애 = 전체 캐시 손실
8. **하이브리드** — L1 = Caffeine (가장 빠름) + L2 = Redis (공유). 실무 패턴

### Spring Cache 추상
9. **`@Cacheable`** = AOP 프록시 (5 주차) — 메서드 호출 가로채서 캐시 확인. 5 주차 self-invocation 함정 동일
10. **CacheManager** — 추상화 인터페이스. ConcurrentMapCacheManager (기본) / CaffeineCacheManager / RedisCacheManager. 코드 안 바뀌고 교체 가능
11. **`@CacheEvict(allEntries = true)`** — 캐시 전체 비우기. 큰 변경 시 (배포 / 정책 변경)

### 캐시 일관성 함정
12. **Stale read** — DB 만 변경하고 캐시 invalidate 안 함 → 옛 값. 변경 메서드에 `@CacheEvict` 필수
13. **Cache stampede** — TTL 만료 시점에 여러 요청 동시 miss → 모두 DB 동시 호출 → DB 폭주
14. **stampede 해결** — (a) 짧은 TTL + jitter (만료 시간 분산) / (b) 분산락 (3 주차) — 한 요청만 DB 가게 / (c) refresh-ahead (만료 전 미리 갱신)
15. **무한 캐시의 위험** — TTL 없이 무한 저장 → 메모리 누수 → OOM. 항상 maximumSize + TTL

### 5 주차 / 8 주차 회수
16. **`@Cacheable` self-invocation** — 5 주차 `@Transactional` / 6 주차 `@Async` 와 정확히 같은 프록시 메커니즘. `this.cachedMethod()` 는 캐시 우회
17. **8 주차 인덱스 vs 9 주차 캐시** — 인덱스 = DB 쿼리 빠르게 / 캐시 = DB 자체 안 가게. 둘은 직교 (orthogonal). 같이 씀

## 자기 검증 (입으로 답할 수 있어야 STAGE 1 시작)

**★ 관문 — 이 3 개는 입으로 답해야 STAGE 1 진입**
- [ ] ★ Cache-Aside 패턴 4 단계 — 1 분 본인 말로
- [ ] ★ 로컬 (Caffeine) vs 분산 (Redis) 트레이드오프
- [ ] ★ Cache stampede 가 무엇이고 해결 3 가지

**보너스 — STAGE 1 진행하면서 답할 수 있게**
- [ ] `@Cacheable` self-invocation 함정 (5 주차 / 6 주차 회수)
- [ ] Stale read 가 발생하는 시나리오 + 해결
- [ ] TTL + jitter 가 stampede 를 어떻게 푸는가
- [ ] CacheManager 교체로 코드 안 바꾸고 Caffeine ↔ Redis
- [ ] `@Cacheable(key = SpEL)` 동적 키 — 5 주차 `@DistributedLock` 과 같은 메커니즘
- [ ] 8 주차 인덱스로 못 푸는 영역 + 9 주차 캐시가 푸는 영역


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 1. 본인 도메인 선택 — 9 주차에 맞게 (조회 잦고 변경 드문)
━━━━━━━━━━━━━━━━━━━━━━━━━━

9 주차 학습 포인트는 **조회가 잦고 결과가 자주 안 바뀌는 도메인** 에서 잘 드러난다. 실시간성이 강한 도메인 (실시간 시세 / 채팅) 은 캐시 일관성 함정이 더 큰 부담 — 학습에는 OK.

## 후보 도메인 + 적합도 (12 개)

| # | 도메인 | 조회 잦음 | 변경 드뭄 | 면접 가치 | 메모 |
|---|---|---|---|---|---|
| 1 | **상품 카탈로그** (`product`) | ★★★ | ★★ | ★★★ | 자주 조회, 가격/재고만 가끔 변경. **캐시 정석** |
| 2 | **인기 게시글** (`hot_post`) | ★★★ | ★★★ | ★★★ | 8 주차 연장. TOP 10 캐싱. 1 분마다 갱신 |
| 3 | **사용자 프로필** (`user_profile`) | ★★★ | ★★★ | ★★ | 자주 읽고 가끔 수정. CacheEvict 학습 |
| 4 | **설정 / 메타데이터** (`config`) | ★★★ | ★★★ | ★★ | 거의 안 바뀜. 무한 캐시 위험 |
| 5 | **환율 / 시세** (`rate`) | ★★★ | ★★ | ★★ | 외부 API 캐싱. TTL 명확 (1 분) |
| 6 | **검색 자동완성** (`autocomplete`) | ★★★ | ★ | ★★ | 같은 prefix 자주 |
| 7 | **카테고리 트리** (`category`) | ★★★ | ★★★ | ★★ | 깊은 조회 + 안 바뀜 |
| 8 | **통계 / 집계** (`stats`) | ★★★ | ★★ | ★★ | 무거운 쿼리. 실시간성 트레이드오프 |
| 9 | **세션 / 인증** (`session`) | ★★★ | ★ | ★★ | 11 주차 인증과 결합 |
| 10 | **알림 카운트** (`notification_count`) | ★★★ | ★ | ★★ | 사용자별 unread count |
| 11 | **공지사항** (`notice`) | ★★ | ★★★ | ★ | 거의 안 바뀜. 무한 캐시 위험 학습 |
| 12 | **쿠폰 정책** (`coupon_policy`) | ★★ | ★★★ | ★★ | 정책 자체는 정적. 3 주차 쿠폰 연장 |

> **캐시 ★★★ 조건** = 같은 키로 자주 조회 + 결과가 자주 안 바뀜. 조회/변경 비율이 100:1 이상 권장.

## 도메인별 예상 시나리오 한 줄

| # | 한 줄 시나리오 |
|---|---|
| 1 | `productRepo.findById(?)` 결과 캐싱. 가격 변경 시 `@CacheEvict`. Caffeine → Redis 전환 |
| 2 | `hotPostsRepo.top10()` 무거운 집계 쿼리 캐싱 + 1 분 TTL |
| 3 | `userRepo.findById(?)` 캐싱. `userService.update` 시 `@CacheEvict` |
| 4 | `configRepo.findByKey(?)` 캐싱. TTL 없이 + 변경 시 명시 evict |
| 5 | 외부 환율 API 결과 캐싱. 1 분 TTL. stampede 방지 (분산락 또는 jitter) |
| 6 | `autocompleteRepo.findByPrefix(?)` — 자주 검색되는 prefix 만 캐싱 |
| 7 | `categoryRepo.findTree()` — 전체 트리 한 번에 캐싱 |
| 8 | `statsRepo.dailySummary(?)` 어제 통계 — 24h TTL |
| 9 | 세션 토큰 → 사용자 정보. Redis 표준 |
| 10 | `notificationRepo.unreadCount(?)` — 변경 시 evict |
| 11 | 공지사항 캐싱. 무한 캐시의 OOM 위험 학습 |
| 12 | 쿠폰 정책 캐싱 + 정책 변경 시 전체 evict |

## 학습자 프로필별 추천

| 본인 상황 | 추천 도메인 |
|---|---|
| 입문자 | **3 사용자 프로필** / **4 설정** — 단순 |
| 8 주차 도메인 연장 | **2 인기 게시글** (게시판 연장) |
| 면접 가치 최대화 | **1 상품 카탈로그** / **8 통계** / **5 환율** |
| Cache stampede 학습 본격 | **2 인기 게시글** / **5 환율** — TTL 짧고 부하 큼 |
| 외부 API 결과 캐싱 | **5 환율** — 외부 API + TTL + stampede 한 세트 |
| 10 주차 (HTTP Pool) 브릿지 | **5 환율** — 외부 호출의 다른 측면 (Pool) 자연 연결 |


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 2. 클래스 구조 매핑
━━━━━━━━━━━━━━━━━━━━━━━━━━

도메인별 추천 클래스 + 캐싱 위치:

| 도메인 | Entity | Service | 캐싱 대상 |
|---|---|---|---|
| 1 상품 | Product | ProductService | findById / 가격 변경 시 evict |
| 2 인기 게시글 | Post | HotPostService | top10() 결과 + 1 분 TTL |
| 3 사용자 프로필 | User | UserService | findById / update 시 evict |
| 4 설정 | Config | ConfigService | findByKey / 명시 evict |
| 5 환율 | (외부 API 응답) | RateService | API 호출 결과 + 1 분 TTL |
| 6 ~ 12 | 비슷한 패턴 | | |

## 공통 — STAGE 1 손 작성 (모두 동일)

`ConcurrentHashMap` 손 캐시 + Caffeine 도입까지:

```java
// (1) 캐시 없이
@Service
public class ProductService {
    private final ProductRepository repo;
    public ProductService(ProductRepository repo) { this.repo = repo; }

    public Product findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}

// (2) ConcurrentHashMap 손 캐시 — TTL 없음, 무한 증가
@Service
public class ProductService {
    private final ProductRepository repo;
    private final Map<Long, Product> cache = new ConcurrentHashMap<>();
    public ProductService(ProductRepository repo) { this.repo = repo; }

    public Product findById(Long id) {
        return cache.computeIfAbsent(id, k -> repo.findById(k).orElseThrow());
    }
}

// (3) Caffeine — maximumSize + expireAfterWrite
@Service
public class ProductService {
    private final ProductRepository repo;
    private final Cache<Long, Product> cache;

    public ProductService(ProductRepository repo) {
        this.repo = repo;
        this.cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .recordStats()
            .build();
    }

    public Product findById(Long id) {
        return cache.get(id, k -> repo.findById(k).orElseThrow());
    }
}
```

> 핵심: (2) → (3) 으로 가는 자리에서 캐시의 **eviction (TTL / 사이즈) + 통계** 가 왜 필요한지 직접 체감.

## measurements.md 형식 (4 ~ 8 주차와 일관)

```
- [09-XX 14:00] s1 · 캐시 없이 같은 id 100 회 조회 — DB 100 회. ____ms
- [09-XX 14:15] s1 · ConcurrentHashMap 캐시 — DB 1 회. ____ms
- [09-XX 14:30] s1 · Caffeine 캐시 — DB 1 회 + hit ratio ____%
- [09-XX 22:00] s2 · @Cacheable 적용 — 코드 25 줄 → 1 줄
- [09-XX 22:15] s2 · @CacheEvict 동작 확인 — update 후 다음 조회 = miss
- [09-XX 22:30] s2 · self-invocation 함정 재현 — this.cached() = DB 매번
- [09-XX 23:00] s3 · Stale read 재현 — DB 변경 + 캐시 안 비움 → 옛 값
- [09-XX 23:15] s3 · Cache stampede 재현 — 동시 100 요청 + TTL 직후 → DB 100 회
- [09-XX 23:30] s4 · Caffeine vs Redis 응답 시간 — μs vs ms
- [09-XX 23:45] s4 · 다중 인스턴스 동기화 — Caffeine 각자 / Redis 공유
```


━━━━━━━━━━━━━━━━━━━━━━━━━━
환경
━━━━━━━━━━━━━━━━━━━━━━━━━━
- Java 21
- **Spring Boot 3.x** + Spring Data JPA + Hibernate (7 주차와 동일)
- **PostgreSQL 16** (8 주차에서 깔아본 그대로) + **Redis 7** (3 주차에서 깔아본 그대로)
- Caffeine — `com.github.ben-manes.caffeine:caffeine`

## build.gradle 추가

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

    // Spring Cache 추상
    implementation 'org.springframework.boot:spring-boot-starter-cache'

    // 로컬 캐시
    implementation 'com.github.ben-manes.caffeine:caffeine'

    // 분산 캐시 (Redis)
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'

    runtimeOnly 'org.postgresql:postgresql'

    // 또는 학습 편의로 H2
    runtimeOnly 'com.h2database:h2'
}
```

## docker-compose.yml

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: cs-study-09-pg
    environment:
      POSTGRES_DB: cache_study
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"

  redis:
    image: redis:7-alpine
    container_name: cs-study-09-redis
    ports:
      - "6379:6379"
```


━━━━━━━━━━━━━━━━━━━━━━━━━━
STAGE 진행 가이드
━━━━━━━━━━━━━━━━━━━━━━━━━━

## 예상 학습 시간 (한 주 기준)

| 단계 | 시간 | 비고 |
|---|---|---|
| STAGE 1 (Cache-Aside 손 작성 + Caffeine) | 2 시간 | **화요일까지 (필수)** |
| **STAGE 2 (Spring Cache + Redis + self-invocation)** ★ | **2 ~ 3 시간** | **목요일까지 (필수)**. 9 주차 가장 중요 |
| STAGE 3 (Stale read + Cache stampede + TTL 전략) | 2 시간 | 함정 직접 재현 |
| STAGE 4 (로컬 vs 분산 + 8 주차 회수) | 1 시간 | 결정 매트릭스 |
| **합계 (필수)** | **7 ~ 9 시간** | |
| STAGE 5 [여유] (캐시 전략 + 10 주차 브릿지) | 30 ~ 60 분 | |

8 주차 (6 ~ 9 시간) 와 비슷한 분량.

### [화 11:00 — Draft PR 마감 + 겪기 발표] — STAGE 1

#### ▸ STAGE 1 — Cache-Aside 손 작성 (필수)

**목표**: 캐시 없는 코드 → 손 캐시 (HashMap) → Caffeine 의 3 단계 진화 직접.

##### 1-1. 캐시 없이 — 매 요청 DB

```java
public Product findById(Long id) {
    return repo.findById(id).orElseThrow();
}

// 같은 id 100 회 호출 → SQL 100 회. show-sql 로그로 확인
```

##### 1-2. 손 캐시 (ConcurrentHashMap) — TTL 없음

```java
private final Map<Long, Product> cache = new ConcurrentHashMap<>();

public Product findById(Long id) {
    return cache.computeIfAbsent(id, k -> repo.findById(k).orElseThrow());
}
```

**관찰 포인트**:
- 같은 id 100 회 호출 → SQL 1 회 + 캐시 99 회 hit
- **문제**: 무한 증가. 1000 만 id 조회하면 메모리 1000 만 객체
- 변경 안 됨 — 무한 캐시는 stale 영구화

##### 1-3. 손 캐시 함정 — 무한 증가 시연

```java
// id 1 ~ 1_000_000 다 조회 → 캐시에 100 만 객체. heap 폭증
for (long i = 1; i <= 1_000_000; i++) {
    service.findById(i);
}
System.out.println("cache size = " + cache.size());      // 1,000,000
// 메모리 사용량 확인 — Runtime.totalMemory() - Runtime.freeMemory()
```

##### 1-4. Caffeine 도입 — maximumSize + expireAfterWrite

```java
private final Cache<Long, Product> cache = Caffeine.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(Duration.ofMinutes(10))
    .recordStats()
    .build();

public Product findById(Long id) {
    return cache.get(id, k -> repo.findById(k).orElseThrow());
}

// 통계
public CacheStats stats() {
    return cache.stats();      // hitRate / evictionCount 등
}
```

**관찰 포인트**:
- maximumSize 10K → 10K 초과 시 LRU 로 오래된 것 제거
- expireAfterWrite 10 분 → 10 분 후 자동 만료
- `stats()` 로 hit ratio 측정. 본 도메인 5 ~ 10 분 운영 후 확인


### [목 11:00 — Ready PR 전환] — STAGE 2 ~ STAGE 4

#### ▸ STAGE 2 — Spring Cache + Redis (필수, **9 주차 가장 중요**)

##### 2-1. `@Cacheable` 한 줄로 추출

```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        // 기본 — ConcurrentMapCacheManager (HashMap 기반, 학습용)
        return new ConcurrentMapCacheManager("products");
    }
}

@Service
public class ProductService {
    private final ProductRepository repo;
    public ProductService(ProductRepository repo) { this.repo = repo; }

    @Cacheable(value = "products", key = "#id")
    public Product findById(Long id) {
        return repo.findById(id).orElseThrow();
    }
}
```

**관찰 포인트**:
- 손 캐시 (Map / Caffeine) 코드 — 사라짐
- `@Cacheable` 한 줄로 끝. 5 주차 `@Audited` 와 같은 추출 패턴 (AOP 프록시)
- `value = "products"` = 캐시 이름 (그룹) / `key = SpEL` = 동적 키

##### 2-2. `@CacheEvict` — 변경 시 캐시 무효화

```java
@CacheEvict(value = "products", key = "#product.id")
@Transactional
public Product update(Product product) {
    return repo.save(product);
}

@CacheEvict(value = "products", allEntries = true)
public void clearAll() {
    // 전체 비우기 — 대량 변경 / 배포 시
}
```

**관찰 포인트**:
- update 후 다음 findById = miss → DB → 새 값 캐싱
- `allEntries = true` 는 전체 비우기. 가격 정책 일괄 변경 같은 케이스

##### 2-3. `@CachePut` — 결과를 캐시에 강제 저장

```java
@CachePut(value = "products", key = "#result.id")
@Transactional
public Product update(Product product) {
    return repo.save(product);
}
```

**관찰 포인트**:
- `@Cacheable` = 캐시 있으면 메서드 안 실행 / `@CachePut` = 메서드 실행 + 결과 캐시
- update 직후 같은 id 조회 → 캐시 hit (방금 저장된 값)
- `@CacheEvict` (비우고 다음 miss 시 새로) vs `@CachePut` (즉시 갱신) — 트레이드오프

##### 2-4. RedisCacheManager 로 교체

```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory cf) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(cf)
            .cacheDefaults(config)
            .build();
    }
}
```

**관찰 포인트**:
- 서비스 코드 (`@Cacheable`) 한 줄도 안 바뀜 — CacheManager 만 교체
- TTL / 직렬화 / 키 prefix 등 설정 분리
- `redis-cli` 로 `keys *` / `get products::42` 확인 가능

##### 2-5. 5 주차 self-invocation 함정 회수

```java
@Service
public class ProductService {
    @Cacheable(value = "products", key = "#id")
    public Product findById(Long id) { /* ... */ }

    public Product findByIdAndCheck(Long id) {
        Product p = this.findById(id);    // ← this 호출 — 캐시 우회!
        // ...
    }
}
```

**관찰 포인트**:
- `this.findById(id)` — 프록시 안 거침 → `@Cacheable` 동작 X → 매번 DB
- 5 주차 `@Transactional` / 6 주차 `@Async` / 7 주차 readOnly 와 정확히 같은 메커니즘
- 해결: (a) 자기 자신 주입 / (b) 클래스 분리 / (c) 이벤트 발행 (6 주차)


#### ▸ STAGE 3 — 캐시 일관성 함정 (필수)

##### 3-1. Stale read — DB 변경 + 캐시 안 비움

```java
// 잘못된 update — @CacheEvict 없음
@Transactional
public Product updatePrice(Long id, BigDecimal newPrice) {
    Product p = repo.findById(id).orElseThrow();
    p.setPrice(newPrice);
    return repo.save(p);
    // 캐시에는 옛 값. 다음 findById = 옛 값 반환 → Stale
}
```

**관찰 포인트**:
- 변경 후 같은 id 조회 → 옛 가격. 사용자에게 잘못된 정보
- 해결: `@CacheEvict` 명시. 또는 짧은 TTL + 약간의 stale 허용

##### 3-2. Cache stampede — TTL 만료 시점 동시 miss

```java
// TTL 1 초 + 동시 100 요청
@Cacheable(value = "rates", key = "#currency")
public BigDecimal getRate(String currency) {
    Thread.sleep(500);     // 외부 API 가 느림
    return externalApi.fetch(currency);
}

// 동시 100 요청 직후 TTL 만료
```

**관찰 포인트**:
- TTL 만료 직후 동시 100 요청 → 모두 miss → 모두 externalApi 호출 → 외부 API 폭주
- 대규모 장애 원인. "썬더링 허드" (thundering herd) 라고도

**해결 3 가지**:
- (a) **TTL + jitter** — 만료 시간을 ±20% 무작위로 분산 → 동시 만료 회피
- (b) **분산락** (3 주차) — 한 요청만 외부 호출, 나머지는 대기 후 캐시 hit
- (c) **refresh-ahead** — TTL 만료 전 미리 백그라운드 갱신 (Caffeine `refreshAfterWrite`)

##### 3-3. 무한 캐시의 위험

```java
// 잘못된 패턴 — TTL / size 제한 없음
@Bean
public CacheManager cacheManager() {
    return new ConcurrentMapCacheManager("unbounded");
    // 캐시 항목 무한 증가. heap OOM
}
```

**관찰 포인트**:
- 1 시간 운영 → 캐시 크기 폭증 → GC 압박 → 응답 지연 → OOM
- 항상 **maximumSize + TTL** 명시
- Redis 도 `maxmemory + maxmemory-policy=allkeys-lru` 설정


#### ▸ STAGE 4 — 로컬 vs 분산 + 8 주차 회수 (필수)

##### 4-1. Caffeine vs Redis 측정

| 항목 | Caffeine | Redis |
|---|---|---|
| 응답 시간 | μs (마이크로초) | ms (밀리초) |
| 다중 인스턴스 동기화 | X (각 JVM 별) | O (공유) |
| 캐시 크기 | JVM 힙 제한 | Redis 메모리 (큼) |
| 장애 영향 | JVM 다운 = 본인 캐시만 | Redis 다운 = 전체 캐시 손실 |
| 운영 비용 | 0 (라이브러리) | Redis 인스턴스 관리 |

##### 4-2. 8 주차 인덱스 vs 9 주차 캐시 — 결정 매트릭스

| 상황 | 선택 |
|---|---|
| 같은 결과 자주 조회 | 캐시 |
| 다양한 조건의 쿼리 | 인덱스 |
| 결과 자주 바뀜 | 인덱스 (캐시 무효화 비용 큼) |
| 결과 거의 안 바뀜 | 캐시 (큰 효과) |
| 외부 API 결과 | 캐시 (인덱스 무관) |
| 무거운 집계 쿼리 | 캐시 (인덱스로 안 풀림) |
| 정확한 실시간 데이터 | 인덱스 (캐시 stale 위험) |

**둘은 직교**: 인덱스 = DB 쿼리 빠르게 / 캐시 = DB 자체 안 가게. 같이 씀.

##### 4-3. JPA 2 차 캐시 — 7 주차 + 9 주차 결합 (살짝)

```java
// 7 주차 영속성 컨텍스트 = 1 차 캐시 (트랜잭션 내)
// 9 주차 Spring Cache = 메서드 레벨
// JPA 2 차 캐시 = EntityManagerFactory 레벨 (애플리케이션 전체)
```

- Hibernate `@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)` Entity 에 적용
- 영속성 컨텍스트 close 후에도 캐시 유지
- 실무 — Spring Cache 가 더 일반적 (도구 일관성). 2 차 캐시는 특수 케이스


━━━━━━━━━━━━━━━━━━━━━━━━━━
─── 여기 아래는 선택 ───
━━━━━━━━━━━━━━━━━━━━━━━━━━

### [선택] ▸ STAGE 5 — 캐시 전략 + 10 주차 브릿지

##### 5-1. 캐시 전략 4 가지

| 전략 | 설명 | 예시 |
|---|---|---|
| Cache-Aside | 앱이 캐시 직접 관리 | Spring `@Cacheable` 기본 |
| Read-Through | 캐시가 DB 읽기 | 라이브러리 처리 |
| Write-Through | 쓰기 시 캐시 + DB 동시 | 일관성 강함, 쓰기 느림 |
| Write-Behind | 캐시 쓰기 후 DB 비동기 | 빠른 쓰기, 손실 위험 |

##### 5-2. 10 주차 (HTTP / Connection Pool) 예고

캐시로 차단 못 하는 외부 호출 — 결제 / 인증 API 등. 호출 자체는 막을 수 없으나 **커넥션 풀** 로 부하 제어. 10 주차 본론.


━━━━━━━━━━━━━━━━━━━━━━━━━━
금지 키워드 — STAGE 1 ~ 2 동안만 (검색 금지)
━━━━━━━━━━━━━━━━━━━━━━━━━━
- Hibernate 2 차 캐시 — STAGE 4-3 에서 살짝만
- CDN / Edge 캐싱 — 본 학습 범위 밖
- Redis Cluster / Sentinel — Redis 인프라 영역
- Spring `@Scheduled` 캐시 워머 — 학습 후
- Cache aside vs Read-through 의 깊은 비교 — STAGE 5 에서 살짝


━━━━━━━━━━━━━━━━━━━━━━━━━━
학습 기록 (자유)
━━━━━━━━━━━━━━━━━━━━━━━━━━

### 8 주차 회상 — 9 주차로 이어지는 지점

| 8 주차 | 9 주차 |
|---|---|
| 인덱스 = SQL 한 회당 시간 단축 | 캐시 = SQL 자체 차단 |
| EXPLAIN 으로 plan 점검 | 캐시 hit / miss 통계 |
| 인덱스로 못 풀리는 부하 | 캐시가 푸는 영역 |
| 옵티마이저가 알아서 | 명시적 `@Cacheable` |

### 9 주차 참고 질문
- Cache-Aside 4 단계 본인 말로
- 로컬 vs 분산 — 본인 도메인 어느 쪽?
- `@Cacheable` self-invocation 함정 (5, 6, 7 주차 회수)
- Cache stampede 해결 3 가지 + 본인 선택
- Stale read 발생 시나리오 + 본인 답
- 8 주차 인덱스 vs 9 주차 캐시 — 결정 매트릭스

### 면접 단골 + 본인 답
- **"Cache-Aside 패턴"** — miss → DB → 캐시 저장 → 반환. hit → 캐시 반환
- **"로컬 vs 분산 캐시"** — Caffeine (μs, JVM 별) vs Redis (ms, 공유)
- **"Cache stampede 와 해결"** — TTL 만료 시 동시 miss → DB 폭주. jitter / 분산락 / refresh-ahead
- **"Stale read"** — DB 변경 후 캐시 안 비움. `@CacheEvict` 또는 짧은 TTL
- **"`@Cacheable` self-invocation"** — 5 주차 `@Transactional` 함정과 동일. 프록시 메커니즘
- **"무한 캐시의 위험"** — maximumSize / TTL 없으면 OOM. 항상 명시
- **"인덱스 vs 캐시"** — 인덱스 = 쿼리 빠르게 / 캐시 = 쿼리 자체 차단. 직교
- **"`@Cacheable` 의 키 설계"** — SpEL `key = "#userId + ':' + #type"`. 5 주차 SpEL 과 같음
- **"`@CacheEvict` vs `@CachePut`"** — 비움 (다음 miss) vs 즉시 갱신
- **"L1 + L2 캐시"** — Caffeine (가장 빠름) + Redis (공유) 하이브리드
- **"Cache Penetration 방어"** — DB 에 없는 키 반복 조회 → 매번 MISS → DB 부하. `@Cacheable(unless = "#result == null")` 로 null 캐싱 안 함 / 또는 의도적 negative caching (null 도 짧은 TTL 로) 도메인 따라 결정
- **"Redis JSON 직렬화의 `@class` 함정"** — `GenericJackson2JsonRedisSerializer` 는 `{"@class":"domain.Product",...}` 처럼 자바 패키지 경로 통째 저장. 클래스 이동 / 리네임 시 역직렬화 폭발 = 운영 장애. 배포 전 캐시 비우기 또는 명시 DTO + 안전한 직렬화기

### 실무 확장 화두
- **`@Cacheable` 의 `sync = true`** — 같은 키 동시 miss 시 한 스레드만 실행 (단일 JVM 한정 stampede 방지)
- **Redis `SETNX` 분산락 + 캐시** — 3 주차 분산락을 stampede 방지에 활용
- **negative caching** — null 도 캐시. DB miss 폭주 방지. 단 메모리 위험
- **캐시 키의 일관성** — 같은 쿼리 다른 키 → 캐시 효과 X. `@Cacheable` 키 설계 신중
- **CDN / Edge 캐시** — 9 주차 범위 밖. HTTP 응답 단위 캐싱
- **JPA 2 차 캐시** — Entity 단위 캐싱 + 영속성 컨텍스트 결합. 실무는 Spring Cache 가 일반
- **캐시 모니터링** — Caffeine `stats()` / Redis `INFO stats` / Micrometer 연동
- **10 주차 Connection Pool 과의 관계** — 캐시로 안 풀리는 외부 호출은 풀로 제어


━━━━━━━━━━━━━━━━━━━━━━━━━━
막힐 때
━━━━━━━━━━━━━━━━━━━━━━━━━━
1. 직접 시도 (`CLAUDE.md` 룰)
2. AI 에 물어보기 — 3 단계 힌트만
3. 디스코드 `#질문` 채널 — `@Cacheable` 코드 + hit/miss 로그 함께

**`@Cacheable` 이 안 먹는 것 같음**:
1. `@EnableCaching` 활성화했는가
2. self-invocation 인가 — 같은 클래스 안 `this.cached()` 호출
3. 메서드가 `public` 인가 (5 주차 / 6 주차 / 7 주차 함정과 동일)
4. CacheManager 빈 등록되어 있는가

**Stale read 발생**:
1. 변경 메서드에 `@CacheEvict` 명시했는가
2. TTL 길어서 만료 안 됐는가
3. 다중 인스턴스 + 로컬 캐시 (Caffeine) — 다른 인스턴스 캐시 못 비움. Redis 로 전환

**Cache stampede 의심**:
1. TTL 만료 시점에 동시 트래픽 폭주
2. 외부 API 호출 시간 길음
3. 해결 — jitter / 분산락 / refresh-ahead

**Redis 연결 안 됨**:
1. docker compose ps 로 redis 컨테이너 확인
2. `redis-cli ping` 으로 pong 확인
3. application.properties 의 `spring.data.redis.host/port`
