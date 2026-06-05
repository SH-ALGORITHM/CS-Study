# 7주차 JPA 영속성 컨텍스트 + N+1 — 용어 정리

> 6 주차의 Spring Event 용어 정리와 같은 형식. STAGE 진행 전 또는 학습 중 막힐 때 참조.
>
> 시나리오 단어표 (15 개) 는 핵심만, 이 파일은 카테고리별 전체.

---

## 🌳 JPA / Hibernate 본질

| 용어 | 풀어쓰면 |
|---|---|
| **ORM** (Object-Relational Mapping) | 객체 ↔ 관계형 DB 매핑. SQL 안 짜고 객체 메서드로 |
| **JPA** (Jakarta Persistence API) | 자바 표준 ORM 명세 (Jakarta EE 9+ 부터 javax → jakarta) |
| **Hibernate** | JPA 의 가장 흔한 구현체. JPA = 명세, Hibernate = 구현 |
| **Spring Data JPA** | Spring 의 JPA 추상화. `Repository<T, ID>` 인터페이스 + 자동 구현 |
| **`@Entity`** | JPA 관리 대상 클래스. PK 필수 (`@Id`). 매개변수 없는 기본 생성자 필수 |
| **`@Id`** | PK 매핑. `@GeneratedValue(strategy = ...)` 로 자동 생성 |
| **`@GeneratedValue`** | PK 자동 생성 전략 — IDENTITY / SEQUENCE / AUTO / TABLE |
| **`EntityManager`** | JPA 핵심 인터페이스. `persist` / `find` / `merge` / `remove` / `createQuery` |
| **`EntityManagerFactory`** | EntityManager 생성기. 보통 컨테이너가 관리 |
| **`@PersistenceContext`** | EntityManager 주입 어노테이션 (Spring 에서는 `@Autowired` 도 OK) |

## 🎭 영속성 컨텍스트 4 마법

| 용어 | 풀어쓰면 |
|---|---|
| **영속성 컨텍스트** (Persistence Context) | EntityManager 가 관리하는 Entity 1 차 캐시 + 상태 추적기 |
| **1 차 캐시** | 같은 트랜잭션 안 같은 id `findById` = 첫 회만 SELECT, 이후 캐시 반환 |
| **변경 감지** (Dirty Checking) | 영속 Entity setter 호출 시 flush 시점에 스냅샷과 비교 → UPDATE 자동 |
| **쓰기 지연** (Write-behind) | `persist` 직후 INSERT 안 나감. flush 시점에 모아서 발행 |
| **동일성 보장** | 같은 트랜잭션 안 같은 id Entity = 같은 인스턴스 (`==` true) |
| **스냅샷** | 영속 시점 Entity 의 복사본. flush 시점에 현재 값과 비교 |
| **`em.flush()`** | 영속성 컨텍스트의 변경을 DB 에 SQL 로 동기화. 강제 호출 |
| **`em.clear()`** | 영속성 컨텍스트 비우기. 모든 Entity 가 준영속 (detached) 상태로 |
| **`em.detach(entity)`** | 특정 Entity 만 컨텍스트에서 분리 |
| **`em.merge(entity)`** | 준영속 Entity 를 다시 영속화 (새 영속 인스턴스 반환, 원본은 그대로 준영속) |
| **`em.contains(entity)`** | Entity 가 영속 상태인지 boolean |

## 🔄 Entity 상태

| 용어 | 풀어쓰면 |
|---|---|
| **비영속** (Transient / New) | `new` 직후. 컨텍스트에 등록 안 됨. PK 없음 |
| **영속** (Managed / Persistent) | `persist` 또는 `find` 후. 컨텍스트가 관리. 변경 감지 대상 |
| **준영속** (Detached) | 컨텍스트가 close 됐거나 `em.detach` 호출. 변경 감지 X. Lazy 접근 시 폭발 |
| **삭제** (Removed) | `em.remove` 후. flush 시점에 DELETE |
| **상태 전이** | new → persist() → managed → detach/clear/close → detached → merge() → managed → remove() → removed |

## ⚙️ flush 모드

| 용어 | 풀어쓰면 |
|---|---|
| **flush 모드 AUTO** (기본) | commit 직전 + JPQL 실행 직전 자동 flush |
| **flush 모드 COMMIT** | commit 직전에만 flush. JPQL 실행 전 flush X (성능) |
| **flush 모드 MANUAL** | `em.flush()` 명시 호출해야만. `@Transactional(readOnly = true)` 가 자동 적용 |
| **`em.flush()` 강제 호출 시점** | 즉시 INSERT/UPDATE 보고 싶을 때 (디버깅) / 다음 쿼리가 변경 사항 봐야 할 때 |

## 🔗 연관 매핑

| 용어 | 풀어쓰면 |
|---|---|
| **`@OneToMany`** | 1:N 관계. 기본 fetch=**LAZY** |
| **`@ManyToOne`** | N:1 관계. 기본 fetch=**EAGER** ← 실무는 LAZY 명시 권장 |
| **`@OneToOne`** | 1:1 관계. 기본 fetch=**EAGER** |
| **`@ManyToMany`** | N:M 관계. 기본 fetch=**LAZY**. **실무는 중간 Entity 권장** — 조인 테이블에 컬럼 추가 불가 (수강 시점 / 점수 등) + 컬렉션 변경 시 전체 DELETE + 재 INSERT 등 동작 함정 |
| **`mappedBy`** | 양방향 연관에서 "주인" 표시. mappedBy 가진 쪽이 "거울" (읽기 전용 매핑) |
| **`@JoinColumn`** | FK 컬럼명 지정. 보통 `@ManyToOne` 쪽에 |
| **단방향 vs 양방향** | 단방향 = 한쪽에서만 참조 / 양방향 = 양쪽 모두 참조. 양방향은 mappedBy + 편의 메서드 필수 |
| **편의 메서드** | 양방향 연관 동기화. `Post.addComment(c) { comments.add(c); c.setPost(this); }` |
| **연관 주인** (Owner) | DB 의 FK 컬럼을 가진 쪽. UPDATE 시 그 쪽 변경만 SQL 반영 |
| **`fetch = LAZY`** | 연관 객체 접근 시점에 SELECT. 프록시 객체로 우선 채워둠 |
| **`fetch = EAGER`** | 처음 SELECT 시 JOIN 으로 함께 가져옴 |

## 🛒 N+1 문제 + 해결

| 용어 | 풀어쓰면 |
|---|---|
| **N+1 문제** | 메인 Entity SELECT 1 회 + 각각의 Lazy 컬렉션 N 회 = 1+N 회 |
| **JOIN FETCH** | JPQL `join fetch` — 한 쿼리로 연관 함께 가져옴 |
| **`@EntityGraph`** | Spring Data 선언형 fetch join. 메서드에 어노테이션 |
| **`@BatchSize(size = N)`** | 컬렉션 / 연관에 N 개씩 IN 묶음. fetch join 의 한계 회피 |
| **`hibernate.default_batch_fetch_size`** | 전역 batch size. application.properties 설정 |
| **`MultipleBagFetchException`** | 컬렉션 2 개 동시 fetch join = Cartesian product 폭증. List → Set 또는 @BatchSize |
| **fetch join + 페이징** | 1:N + Pageable = `HHH000104` WARN + 메모리 페이징. 위험 |
| **`@OneToMany` 기본 LAZY** | N+1 의 원천. JOIN FETCH / @BatchSize 로 case-by-case |
| **`@ManyToOne` EAGER 함정** | 의도와 다른 SELECT. 실무는 LAZY 명시 |
| **distinct fetch** | `select distinct p from Post p join fetch p.comments` — 자바 객체 중복 제거. Hibernate 6.x+ 기본 동작 |
| **2-phase fetch** | 1 단계: Post ID 페이징 / 2 단계: Post + Comment fetch join. 페이징 + 컬렉션 해결 |

## ⚠️ Lazy 함정 + OSIV

| 용어 | 풀어쓰면 |
|---|---|
| **`LazyInitializationException`** | 영속성 컨텍스트 close 후 Lazy 접근. detached Entity 의 Lazy 컬렉션 / 객체 fetch 불가 |
| **`Hibernate.initialize(entity.field)`** | 트랜잭션 안에서 강제 초기화 (Lazy 강제 fetch) |
| **DTO 변환** | 트랜잭션 안에서 `new Dto(entity)` 변환 후 반환. 가장 권장 |
| **OSIV** (Open Session In View) | 영속성 컨텍스트가 컨트롤러 / View 렌더링까지 유지. Spring Boot 기본 ON |
| **OSIV 의 정확한 범위** | **서블릿 요청 컨텍스트 안에서만 동작** (DispatcherServlet → OSIV 인터셉터). `main` 직접 호출 / `@Async` 스레드 / 배치 작업은 OSIV 와 무관 → 폭발 |
| **`spring.jpa.open-in-view=true`** | OSIV 기본 — 웹 요청 안 Lazy 안전, 학습 편의 |
| **`spring.jpa.open-in-view=false`** | 운영 권장 — 커넥션 풀 점유 최소화. 서비스 계층 DTO 변환 강제 |
| **OSIV 트레이드오프** | 켜기 = 웹 안 Lazy 안전 + 응답 끝까지 커넥션 점유 / 끄기 = 안전 X + 커넥션 짧게 |
| **준영속 상태 setter** | 그냥 자바 객체 setter — DB 반영 X. 다시 영속화 (`em.merge`) 해야 |

## 🔄 @Transactional + 영속성 컨텍스트 결합

| 용어 | 풀어쓰면 |
|---|---|
| **트랜잭션 = 영속성 컨텍스트 수명** | `@Transactional` 진입 = EM 생성 / 종료 = EM close |
| **`TransactionSynchronizationManager`** | 5 주차 ThreadLocal 회수 — Spring 이 EM 도 같이 관리 |
| **`@Transactional(readOnly = true)`** | 스냅샷 X / 변경 감지 X / flush 모드 MANUAL. 조회 전용 |
| **`Propagation.REQUIRED`** (기본) | 진행 중 트랜잭션 있으면 참여, 없으면 새로. 영속성 컨텍스트도 공유 |
| **`Propagation.REQUIRES_NEW`** | 새 트랜잭션 강제. 새 영속성 컨텍스트 → 호출자와 격리 |
| **`Propagation.NESTED`** | SAVEPOINT 기반 부분 롤백. 영속성 컨텍스트는 공유 |
| **`Propagation.SUPPORTS`** | 진행 중 트랜잭션 있으면 참여, 없으면 트랜잭션 없이. 영속성 컨텍스트도 트랜잭션 없으면 매번 새로 close |
| **`@Async + AFTER_COMMIT` 함정** | 6 주차 회수. 새 스레드 = 영속성 컨텍스트 X. `REQUIRES_NEW` 명시 |
| **`Repository` 메서드 기본 트랜잭션** | Spring Data JPA 가 자동 `@Transactional` (`save` 는 REQUIRED, `find` 는 readOnly) |

## 🧪 SQL 로그 / 측정 도구

| 용어 | 풀어쓰면 |
|---|---|
| **`spring.jpa.show-sql=true`** | Hibernate 가 생성한 SQL 콘솔 출력 |
| **`hibernate.format_sql=true`** | SQL 보기 좋게 들여쓰기 |
| **`hibernate.use_sql_comments=true`** | SQL 옆에 JPQL / 호출 위치 코멘트 |
| **logback `org.hibernate.SQL=DEBUG`** | 위와 동일 효과. logback 으로 제어 |
| **logback `org.hibernate.orm.jdbc.bind=TRACE`** | 바인딩 파라미터 (`?` 자리 값) 출력 |
| **p6spy** | DataSource 프록시. 실제 SQL + 파라미터 + 실행 시간 로그 |
| **datasource-proxy** | 비슷한 도구. 쿼리 카운트 / 슬로우 쿼리 추적 |
| **JPA Buddy** | IntelliJ 플러그인. JPA 모델 시각화 + Entity 생성 도구 |
| **`em.unwrap(SessionImplementor).getStatistics()`** | Hibernate Statistics — 쿼리 수 / 캐시 hit 등 측정 |

## 🌟 6 주차 결합 + 8 주차 브릿지

| 용어 | 풀어쓰면 |
|---|---|
| **`AbstractAggregateRoot<T>`** | Spring Data JPA. `registerEvent(event)` 한 줄로 이벤트 큐 등록 |
| **`@DomainEvents`** | Aggregate Root 의 메서드. `repository.save()` 호출 시 자동 publish |
| **`@AfterDomainEventPublication`** | 발행 후 이벤트 큐 정리 메서드. `AbstractAggregateRoot` 가 기본 구현 |
| **Aggregate Root** (DDD) | 도메인 객체 군의 진입점. 외부에서는 Root 만 참조 |
| **`@DomainEvents` vs `publishEvent`** | publisher 주입 불필요 / `save` 거쳐야 발행 / 도메인 객체 순수 |
| **8 주차 인덱스 브릿지** | 7 주차에서 본 JPA 생성 SQL → 8 주차 `EXPLAIN` / 인덱스 사용 여부 점검 |
| **JPA 가 만든 SQL 의 슬로우 쿼리** | 8 주차 본론. fetch join 결과 행 폭증 → 인덱스 추가 / 쿼리 재작성 |

## 💾 캐스케이드 / orphanRemoval

| 용어 | 풀어쓰면 |
|---|---|
| **`cascade = CascadeType.PERSIST`** | 부모 persist 시 자식도 자동 persist |
| **`cascade = CascadeType.REMOVE`** | 부모 remove 시 자식도 자동 remove |
| **`cascade = CascadeType.ALL`** | 모든 작업 전파 |
| **`orphanRemoval = true`** | 부모 컬렉션에서 자식 제거 시 자동 DELETE |
| **Aggregate Root 패턴** | cascade + orphanRemoval 로 Root 만 관리 — 자식 lifecycle 은 Root 가 책임 |

## 🧱 학습 도구

| 용어 | 풀어쓰면 |
|---|---|
| **`spring-boot-starter-data-jpa`** | JPA + Hibernate + Spring Data 묶음 |
| **`spring.jpa.hibernate.ddl-auto`** | create / create-drop / update / validate / none. 학습 = create / 운영 = validate or none |
| **H2 인메모리** | 학습용. `jdbc:h2:mem:test;DB_CLOSE_DELAY=-1` |
| **Lombok `@Getter` `@NoArgsConstructor`** | Entity 보일러플레이트. 단 `@AllArgsConstructor` + `@Builder` 는 신중 (불변성) |
| **`@Embeddable` / `@Embedded`** | Entity 안에 값 객체 (Value Object) 매핑 — VO 가 별도 테이블 X, 같은 테이블 컬럼 |
| **`@Column`** | 컬럼 매핑. nullable / unique / length 등 |
| **`-parameters`** | 5, 6 주차에서 익힘. JPQL 파라미터 바인딩 안전장치 |

---

## ★ STAGE 1 진입 관문 (입으로 답)

1. **영속성 컨텍스트 4 마법** — 1 차 캐시 / 변경 감지 / 쓰기 지연 / 동일성 보장
2. **N+1 발생 원리 + 해결 4 가지** — fetch=LAZY + 컬렉션 순회. JOIN FETCH / @EntityGraph / @BatchSize / 전역
3. **`LazyInitializationException` 발생 조건** — 영속성 컨텍스트 close 후 Lazy 접근. 6 주차 `@Async + AFTER_COMMIT` 과 동일 메커니즘

## ★ STAGE 2 진입 관문 (7 주차 가장 중요)

1. **JOIN FETCH vs `@EntityGraph` vs `@BatchSize`** — 각각의 장점 / 한계. 실무 권장 조합
2. **fetch join + 페이징 한계** — `HHH000104` WARN + 메모리 페이징. 컬렉션 1 개 fetch + 나머지 @BatchSize
3. **`MultipleBagFetchException`** — 컬렉션 2 개 동시 fetch 폭증. List → Set 또는 @BatchSize
