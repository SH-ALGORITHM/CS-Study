# 7주차 예시 코드 — 게시판 (JPA 영속성 컨텍스트 + N+1)

scenario.md 의 12 개 도메인과 **별개로** 만든 참고 코드입니다.
6 주차의 이벤트 메커니즘이 시간축 분리였다면, **이번엔 SQL 자체를 안 쓰는 영속성 컨텍스트의 4 마법 + N+1**.

> ⚠️ **베끼지 마시고 본인 도메인으로 변환하세요.** "이런 식으로 흘러가는구나" 감을 잡는 용도.

## 6 주차와 무엇이 같고 다른가

| | 6 주차 Event | 7 주차 JPA 영속성 컨텍스트 |
|---|---|---|
| 풀려고 하는 문제 | 한 사건이 여러 모듈로 퍼지기 (commit 후) | SQL 안 쓰고 객체 변경만으로 INSERT/UPDATE 자동 |
| 도구 | `ApplicationEventPublisher` / `@TransactionalEventListener` | `EntityManager` / `@Entity` / `@OneToMany` |
| 메커니즘 | 시간축 (commit 전 / 후 phase) | 상태 추적 (1 차 캐시 + 변경 감지 + 쓰기 지연) |
| 면접 직결 | 4 phase / `@Async` 함정 / AOP vs Event | 영속성 컨텍스트 4 마법 / N+1 / Lazy 함정 |
| 6 주차 함정 | self-invocation / AFTER_COMMIT no-op / @Async 예외 | **6 주차 @Async + AFTER_COMMIT 의 새 스레드 = 영속성 컨텍스트 없음 → Lazy 폭발** |

핵심: 6 주차 `@Async + AFTER_COMMIT` 의 함정이 7 주차 영속성 컨텍스트와 만나면 `LazyInitializationException` 으로 완성. STAGE 3-3 에서 직접 재현.

## 폴더 구조

> 📌 **5 주차와 같은 공용 `domain/` 구조** — JPA Entity 는 공유 자원이라 stage 마다 inner 로 만들면 매번 다른 테이블 / DDL 충돌. 6 주차의 self-contained 와 다름.

```
example/
├── README.md                              # 지금 이 파일
├── build.gradle                           # Spring Boot 3.x + data-jpa + h2
├── src/main/
│   ├── java/
│   │   ├── domain/                             # 공용 Entity + Repository
│   │   │   ├── Author.java
│   │   │   ├── Post.java                       # @ManyToOne Author / @OneToMany Comment + @BatchSize
│   │   │   ├── Comment.java
│   │   │   ├── PostRepository.java             # findAllWithCommentsJoinFetch / EntityGraph / Paged
│   │   │   ├── AuthorRepository.java
│   │   │   └── CommentRepository.java
│   │   ├── infra/
│   │   │   ├── MeasurementLog.java
│   │   │   └── SchemaSeeder.java               # Post / Comment 테스트 데이터
│   │   └── stage/
│   │       ├── s1/                             # STAGE 1: 영속성 컨텍스트 4 마법
│   │       │   ├── Stage1_1_WriteBehind.java       # 쓰기 지연
│   │       │   ├── Stage1_2_FirstCache.java        # 1 차 캐시 + 동일성
│   │       │   ├── Stage1_3_DirtyChecking.java     # 변경 감지
│   │       │   └── Stage1_4_ContextLifecycle.java  # 트랜잭션 = 컨텍스트 수명
│   │       ├── s2/                             # STAGE 2: N+1 ★ (7 주차 가장 중요)
│   │       │   ├── Stage2_1_NPlusOne.java          # 재현
│   │       │   ├── Stage2_2_JoinFetch.java         # JPQL JOIN FETCH
│   │       │   ├── Stage2_3_EntityGraph.java       # @EntityGraph 선언형
│   │       │   ├── Stage2_4_BatchSize.java         # @BatchSize IN 절 묶음
│   │       │   └── Stage2_5_FetchJoinLimits.java   # 페이징 한계 (HHH000104)
│   │       ├── s3/                             # STAGE 3: Lazy 함정
│   │       │   ├── Stage3_1_LazyException.java     # LazyInitializationException
│   │       │   ├── Stage3_2_DtoSolution.java       # DTO 변환 (권장)
│   │       │   └── Stage3_3_AsyncContextLoss.java  # 6 주차 @Async + AFTER_COMMIT 회수
│   │       └── s4/                             # STAGE 4: @Transactional 결합
│   │           ├── Stage4_1_ReadOnly.java          # readOnly=true 효과
│   │           └── Stage4_2_RequiresNew.java       # REQUIRES_NEW 새 영속성 컨텍스트
│   └── resources/
│       └── application.properties              # H2 인메모리 + show_sql + OSIV
```

## 실행 방법

```bash
cd topics/07-jpa-n-plus-1/example

# STAGE 1-1 쓰기 지연
./gradlew run -PmainClass=stage.s1.Stage1_1_WriteBehind

# STAGE 1-3 변경 감지 (가장 마법 같은 자리)
./gradlew run -PmainClass=stage.s1.Stage1_3_DirtyChecking

# STAGE 2-1 N+1 재현
./gradlew run -PmainClass=stage.s2.Stage2_1_NPlusOne

# STAGE 2-2 JOIN FETCH 해결
./gradlew run -PmainClass=stage.s2.Stage2_2_JoinFetch

# STAGE 2-5 fetch join 한계 (HHH000104 WARN)
./gradlew run -PmainClass=stage.s2.Stage2_5_FetchJoinLimits

# STAGE 3-3 6 주차 @Async 함정 회수
./gradlew run -PmainClass=stage.s3.Stage3_3_AsyncContextLoss

# ... 나머지도 동일
```

## 핵심 학습 흐름

1. **STAGE 1** — 영속성 컨텍스트 4 마법 (1 차 캐시 / 변경 감지 / 쓰기 지연 / 동일성) 을 SQL 로그로 직접 관찰
2. **STAGE 2** ★ — N+1 재현 → JOIN FETCH → @EntityGraph → @BatchSize → 페이징 한계까지. **7 주차 가장 중요한 학습**
3. **STAGE 3** — Lazy 폭발 → DTO 변환 → 6 주차 @Async + AFTER_COMMIT 함정 회수
4. **STAGE 4** — readOnly 효과 + REQUIRES_NEW 새 컨텍스트

> **STAGE 2 가 7 주차 가장 중요한 학습**. N+1 재현 → 해결 4 가지 → 한계까지 한 흐름으로.

## SQL 로그 보는 법

`spring.jpa.show-sql=true` + `hibernate.format_sql=true` 가 켜져 있어 콘솔에 다음과 같이 찍힘:

```
Hibernate:
    select
        p1_0.id,
        p1_0.author_id,
        p1_0.title
    from
        post p1_0
```

각 stage 의 `MeasurementLog.marker("[A]")` 와 SQL 로그 순서를 비교하면 영속성 컨텍스트가 언제 SQL 을 발행하는지 명확하게 보임.
