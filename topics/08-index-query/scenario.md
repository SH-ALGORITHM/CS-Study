# 8주차 — 그 SQL 이 실제 DB 에서 어떻게 실행되는가 (인덱스 + EXPLAIN + 쿼리 튜닝)

이번 주제: 7 주차에 JPA 가 자동으로 만들어주는 SQL 을 봤다. 그 SQL 이 **실제 DB 에서 100 만 row 를 어떻게 훑는지** 는 또 다른 세계. 인덱스 없으면 100 만 회 비교 (Full Scan), 인덱스 있으면 B+Tree 높이만큼만 (3 ~ 4 회 IO). 8 주차는 EXPLAIN 으로 실행 계획을 직접 읽고, 인덱스가 **언제 안 먹는지 6 가지 케이스** 를 손으로 재현한다. 그리고 7 주차에서 본 N+1 / JOIN FETCH 가 만든 SQL 도 같은 EXPLAIN 으로 점검.

5 가지 학습 축:
- **B+Tree 인덱스 자료구조** — 왜 빠른가 (높이 3 ~ 4 로 1M row 도 3 회 IO) / 왜 INSERT/UPDATE 가 느려지는가 (트리 재구성)
- **EXPLAIN / 실행 계획 읽기** — Seq Scan / Index Scan / cost / rows / actual time / Buffers
- **인덱스 미사용 6 케이스** ★ — 함수 / LIKE 와일드카드 위치 / OR / 묵시적 형변환 / NULL / 부정형 (`<>`)
- **복합 인덱스 + 커버링 인덱스** — leftmost prefix 규칙 / `Index Only Scan`
- **7 주차 JPA SQL EXPLAIN** — N+1 / JOIN FETCH / @BatchSize 의 실제 plan 분석 (살짝)

---

## 우선 알아둬야 할 단어 (시작 전 1 분)

| 단어 | 풀어쓰면 |
|---|---|
| **인덱스** (Index) | DB 테이블의 "책 뒤 색인". 특정 컬럼 값 → row 위치 빠르게 찾기 |
| **B+Tree** | 인덱스의 기본 자료구조. 높이 3 ~ 4 로 수백만 row 도 빠르게. 정렬 / 범위 검색 강함 |
| **Hash Index** | 해시 기반. 동등 비교 (`=`) 만. 범위 / 정렬 X. PostgreSQL 은 거의 안 씀 |
| **Full Scan / Seq Scan** | 테이블 전체 순차 스캔. 인덱스 안 쓰는 경우 |
| **Index Scan** | 인덱스 따라가서 row 위치 찾고 테이블 접근 |
| **Index Only Scan** | 인덱스만으로 결과 완성 (테이블 접근 X). **커버링 인덱스** |
| **EXPLAIN** | DB 가 어떤 plan 으로 쿼리 실행할지 예측 출력 |
| **EXPLAIN ANALYZE** | 실제 실행 + 측정 시간 / 행 수 같이. **본격 학습 도구** |
| **cost** | 옵티마이저의 비용 추정 (단위 무의미, 상대 비교용) |
| **rows** | 예상 결과 행 수. 실제와 차이 크면 통계 부정확 |
| **actual time** | EXPLAIN ANALYZE 시 실제 소요 시간 (ms) |
| **Cardinality** | 컬럼의 unique 값 수. 높을수록 인덱스 효과 큼 (`user_id` ★★★ / `gender` ★) |
| **Selectivity** | 조건이 걸러내는 비율. 1 / Cardinality 비슷한 의미 |
| **복합 인덱스** | 컬럼 N 개 묶은 인덱스. 순서 중요 (leftmost prefix) |
| **leftmost prefix** | (a, b, c) 인덱스 → `a` / `a, b` / `a, b, c` 만 사용. `b` 만 X / `b, c` 만 X |

> 📚 더 깊은 용어 (B+Tree 자세히 / pg_stat_statements / 통계 / VACUUM 등) — [`terms.md`](terms.md) 참고. 7 주차와 같은 형식.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 0. 개념 숙지 (도메인 무관, 모두 동일)
━━━━━━━━━━━━━━━━━━━━━━━━━━

## 필수 개념 (AI 와 대화하며 숙지)

### 7 주차 → 8 주차 연결
1. **JPA 가 만든 SQL 의 비용** — 7 주차에서 N+1 = 1 + N 회 SQL 봤다. 100 회 SQL × DB 50ms = 5 초. 8 주차는 그 50ms 가 어떻게 결정되는가
2. **EXPLAIN 으로 점검 가능** — 본인이 짠 JPQL 이든 JPA 자동 SQL 이든 결국 SQL. DB 의 옵티마이저 plan 은 같은 EXPLAIN

### B+Tree 인덱스 본질
3. **왜 B+Tree** — 디스크 IO 최소화. 노드 1 개 = 디스크 페이지 1 개. 높이 3 ~ 4 로 수백만 row 검색 (1M row = log_100(1M) ≈ 3)
4. **B+Tree 의 정렬 / 범위 강점** — 리프 노드가 연결 리스트 → `WHERE id BETWEEN 100 AND 200` 빠름. `ORDER BY id` 도 별도 Sort 불필요
5. **인덱스의 비용** — INSERT/UPDATE/DELETE 시 트리 재구성. 인덱스 5 개 = INSERT 5 번 더 일함. 디스크도 인덱스 크기만큼 더 씀

### EXPLAIN 읽기
6. **Seq Scan vs Index Scan vs Index Only Scan** — 순차 / 인덱스 / 인덱스만. 후자일수록 빠름
7. **cost / rows / actual time / Buffers** — cost 는 추정 (상대) / rows 는 예상 행 수 / actual time 은 실제 / Buffers 는 page 수
8. **rows 예상 vs 실제 차이** — 옵티마이저 통계 부정확 신호. `ANALYZE table` 로 통계 갱신

### 인덱스 미사용 6 케이스 (★ 7 주차 N+1 만큼 중요)
9. **(a) 함수 적용** — `WHERE LOWER(name) = 'kim'` → 인덱스 X. 해결 = 함수 인덱스 (`CREATE INDEX ON t (LOWER(name))`)
10. **(b) LIKE 와일드카드 위치** — `LIKE '김%'` OK / `LIKE '%김'` X / `LIKE '%김%'` X. **앞 와일드카드 = 풀스캔**
11. **(c) OR 조건** — `WHERE a = ? OR b = ?` 시 두 컬럼 모두 인덱스 있어야. 한쪽 없으면 전체 풀스캔
12. **(d) 묵시적 형변환** — `WHERE varchar_col = 12345` (숫자) → DB 가 컬럼 쪽을 캐스팅 → 인덱스 X
13. **(e) NULL 처리** — `IS NULL` 은 일부 DB 에서 인덱스 사용 / `IS NOT NULL` 은 보통 X (PostgreSQL 은 IS NULL 도 OK)
14. **(f) 부정형** — `<>`, `NOT IN`, `NOT EXISTS` 보통 X. 옵티마이저가 풀스캔 선호

### 복합 인덱스 + leftmost prefix
15. **`(a, b, c)` 복합 인덱스** — 사용 가능: `a` / `a, b` / `a, b, c`. **사용 불가**: `b` 만 / `b, c` 만 / `c` 만
16. **순서 결정 기준** — Cardinality 높은 컬럼 → 등호 조건 → 범위 조건 순. 자주 같이 쓰는 컬럼 묶기
17. **커버링 인덱스** (Covering Index) — SELECT 컬럼이 모두 인덱스 안에 있으면 테이블 접근 X → `Index Only Scan`. PostgreSQL 은 `INCLUDE` 절로 명시 가능

### 7 주차 JPA SQL → 8 주차 EXPLAIN
18. **N+1 SQL 의 plan** — `SELECT * FROM comment WHERE post_id = ?` N 회 발행. `post_id` 인덱스 있으면 한 회당 빠름. 단 **N 회 자체의 오버헤드** (네트워크 / 파싱) 는 인덱스로 안 풀림 → JOIN FETCH 또는 @BatchSize 필요
19. **JOIN FETCH 의 plan** — LEFT OUTER JOIN. 옵티마이저가 Hash Join / Nested Loop / Merge Join 중 선택. 결과 row 수 폭증 (Cartesian) 시 비싸짐
20. **OFFSET 큰 페이징 함정** — `LIMIT 10 OFFSET 100000` → 100010 row 읽고 10 만 버림. 해결 = cursor pagination (`WHERE id > ? LIMIT 10`)

## 자기 검증 (입으로 답할 수 있어야 STAGE 1 시작)

**★ 관문 — 이 3 개는 입으로 답해야 STAGE 1 진입**
- [ ] ★ B+Tree 인덱스가 왜 빠른가 — 1 분 본인 말로 (높이 / IO 최소화)
- [ ] ★ 인덱스 미사용 6 케이스 중 본인이 가장 자주 만날 3 개
- [ ] ★ 복합 인덱스 leftmost prefix 규칙 — `(a, b, c)` 에서 사용 / 미사용 케이스

**보너스 — STAGE 1 진행하면서 답할 수 있게**
- [ ] EXPLAIN 의 cost / rows / actual time 각각 의미
- [ ] Seq Scan / Index Scan / Index Only Scan 차이
- [ ] Cardinality 높은 컬럼 / 낮은 컬럼 — 인덱스 효과 차이 본인 예
- [ ] 인덱스 추가의 트레이드오프 (조회 빠름 / 변경 느림 / 디스크)
- [ ] 7 주차 N+1 의 SQL N 회를 EXPLAIN 으로 보면 회당 무엇이 나오나
- [ ] OFFSET 큰 페이징 함정 + cursor pagination 본인 답
- [ ] 함수 인덱스 / 부분 인덱스 / 표현식 인덱스 차이


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 1. 본인 도메인 선택 — 8 주차에 맞게 (대용량 + 다양한 쿼리 패턴)
━━━━━━━━━━━━━━━━━━━━━━━━━━

8 주차 학습 포인트 (**EXPLAIN / 인덱스 미사용 / 복합 인덱스**) 는 **row 가 충분히 많고 (10 만+ 권장) 쿼리 패턴이 다양한 도메인** 에서 잘 드러난다. row 가 적으면 (1 만 미만) 옵티마이저가 그냥 Seq Scan 선택 → 인덱스 효과 안 보임.

## 옵션 — 7 주차 도메인 그대로 vs 새 도메인

| 옵션 | 권장 대상 | 흐름 |
|---|---|---|
| **A. 7 주차 도메인 그대로 + 더미 데이터 100 만 건** | 도메인 새로 짜기 부담 | 7 주차 Entity 재사용. seed 만 100 만으로. 본인이 짠 JPQL 의 EXPLAIN 직접 보기 |
| **B. 새 도메인 선택** | 대용량 데이터 자연스러운 도메인 (로그 / 시계열) | STEP 1 후보표에서 |
| **C. 혼합** | 무난 | STAGE 1 ~ 3 공통 학습 도메인 (게시판) / STAGE 4 부터 본인 7 주차 도메인 |

**모두 STAGE 1 (B+Tree + EXPLAIN 손 작성) 은 공통.** 본인 도메인 무관.

## 후보 도메인 + 적합도 (12 개 — 7 명이 1 개씩 + 여유 5)

| # | 도메인 | 대용량 자연 | 쿼리 다양 | 면접 가치 | 메모 |
|---|---|---|---|---|---|
| 1 | **게시판** (`board`) | ★★★ | ★★★ | ★★★ | 7 주차 연장. 100 만 게시글 + 1000 만 댓글. 페이징 + 정렬 자연 |
| 2 | **이커머스 주문** (`order`) | ★★★ | ★★★ | ★★★ | 6 / 7 주차 연장. 다중 인덱스 + 복합 인덱스 학습 강 |
| 3 | **로그인 / 감사 로그** (`audit_log`) | ★★★ | ★★ | ★★★ | 시간 범위 쿼리 + 인덱스 필수. 시계열 데이터 |
| 4 | **채팅 / 메시지** (`chat`) | ★★★ | ★★★ | ★★★ | 시간순 페이징 + cursor pagination 학습 강 |
| 5 | **결제 내역** (`payment`) | ★★ | ★★ | ★★ | 단순. 입문자용 |
| 6 | **쿠폰 발급** (`coupon`) | ★★ | ★★ | ★★ | 3 주차 연장. 발급자 X 쿠폰 인덱스 |
| 7 | **검색 로그** (`search_log`) | ★★★ | ★★★ | ★★ | LIKE 와일드카드 자연. 전문 검색 (FTS) 살짝 |
| 8 | **알림** (`notification`) | ★★★ | ★★ | ★★ | 사용자별 + 읽음 / 안읽음 = 복합 인덱스 |
| 9 | **상품 카탈로그** (`product`) | ★★ | ★★★ | ★★ | 다중 정렬 + 필터 (가격 / 카테고리 / 평점) |
| 10 | **배송 추적** (`delivery`) | ★★ | ★★ | ★★ | 운송장 번호 unique 인덱스 |
| 11 | **좋아요** (`like`) | ★★★ | ★★ | ★★ | count() 집계 + 인덱스 |
| 12 | **팔로우** (`follow`) | ★★★ | ★★ | ★★ | M:N + 양방향. follower / followee 양쪽 인덱스 |

> **대용량 자연 ★★★ 조건** = 실무에서 1000 만 ~ 1 억 row 가 자연스러움. 시계열 / 로그 / 활동 기록류

## 도메인별 예상 시나리오 한 줄

| # | 한 줄 시나리오 |
|---|---|
| 1 | 100 만 게시글 + 1000 만 댓글. `WHERE author_id=? ORDER BY created_at DESC LIMIT 10` EXPLAIN |
| 2 | 100 만 주문 + 500 만 주문 아이템. `(user_id, created_at)` 복합 인덱스 + 커버링 |
| 3 | 1000 만 로그인 기록. 시간 범위 + 사용자 필터 인덱스 학습 |
| 4 | 1000 만 메시지. 채팅방별 cursor pagination — OFFSET 함정 극복 |
| 5 | 100 만 결제. 상태별 필터 — `WHERE status='SUCCESS'` cardinality 낮은 함정 |
| 6 | 1000 만 쿠폰 발급. `(user_id, coupon_id)` 복합 unique 인덱스 |
| 7 | 1000 만 검색어. `LIKE '%word%'` 풀스캔 함정 + 전문 검색 인덱스 |
| 8 | 1000 만 알림. `(user_id, is_read)` 복합 + 부분 인덱스 (`WHERE is_read = false`) |
| 9 | 100 만 상품. 다중 정렬 (`ORDER BY price, rating DESC`) 복합 인덱스 |
| 10 | 100 만 배송. 운송장 번호 unique 인덱스 + 상태 변경 이력 |
| 11 | 1000 만 좋아요. `(post_id, user_id)` 복합 + count() 통계 |
| 12 | 1000 만 팔로우. `(follower_id, followee_id)` + 역방향 `(followee_id, follower_id)` 양쪽 인덱스 |

## 학습자 프로필별 추천

| 본인 상황 | 추천 도메인 |
|---|---|
| 인덱스 처음 / 입문자 | **1 게시판** / **5 결제** — 단순 + 자료 풍부 |
| 7 주차 도메인 연장 | **1 게시판** (7 주차 example 도메인 그대로) |
| 면접 가치 최대화 | **1 게시판** / **2 주문** / **3 감사 로그** / **8 알림** |
| 시계열 / 로그 학습 강 | **3 감사 로그** / **7 검색 로그** / **4 채팅** |
| 복합 인덱스 + 커버링 본격 | **2 주문** / **8 알림** / **11 좋아요** |
| cursor pagination 학습 | **4 채팅** / **11 좋아요** |
| 9 주차 (캐시) 자연 브릿지 | **1 게시판** / **9 상품 카탈로그** — 인덱스로 안 풀리면 캐시 |


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 2. 클래스 구조 매핑
━━━━━━━━━━━━━━━━━━━━━━━━━━

8 주차는 SQL / 인덱스 학습이라 Entity 구조보다 **데이터량 + 쿼리 패턴** 이 핵심. 도메인별 추천:

| 도메인 | Entity / 컬럼 | 핵심 쿼리 패턴 |
|---|---|---|
| 1 게시판 | Post(id, title, content, author_id, created_at) / Comment | `WHERE author_id=? ORDER BY created_at DESC LIMIT 10` |
| 2 주문 | Order(id, user_id, status, total, created_at) / OrderItem | `WHERE user_id=? AND status='PAID' ORDER BY created_at DESC` |
| 3 감사 로그 | AuditLog(id, user_id, action, target_id, created_at) | `WHERE created_at BETWEEN ? AND ? AND action=?` |
| 4 채팅 | Message(id, room_id, sender_id, content, sent_at) | `WHERE room_id=? AND id < ? ORDER BY id DESC LIMIT 50` |
| 5 ~ 12 | 비슷한 패턴 | |

## 공통 — STAGE 1 손 작성 (모두 동일)

PostgreSQL + 100 만 row seed + EXPLAIN ANALYZE 한 사이클:

```sql
-- 100 만 row seed
INSERT INTO post (title, author_id, created_at)
SELECT 'Post #' || i, (i % 1000) + 1, NOW() - (i || ' seconds')::interval
FROM generate_series(1, 1000000) AS i;

-- 통계 갱신
ANALYZE post;

-- 인덱스 없이 — Seq Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE author_id = 42 ORDER BY created_at DESC LIMIT 10;

-- 인덱스 추가
CREATE INDEX idx_post_author_created ON post(author_id, created_at DESC);

-- 같은 쿼리 — Index Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE author_id = 42 ORDER BY created_at DESC LIMIT 10;

-- 측정 비교: Seq Scan 200ms vs Index Scan 0.5ms
```

> 핵심: 같은 쿼리 / 같은 데이터에서 인덱스 하나로 400 배 차이. 8 주차의 본질.

## measurements.md 형식 (4 ~ 7 주차와 일관)

자동 누적 형식 그대로:
```
- [08-XX 14:00] s1 · Seq Scan 100 만 row — ____ms
- [08-XX 14:15] s1 · 인덱스 추가 후 — ____ms (배수: ____)
- [08-XX 14:30] s1 · INSERT 인덱스 0 / 1 / 5 개 비교 — ____ ms / ____ ms / ____ ms
- [08-XX 22:00] s2 · WHERE LOWER(title) — Seq Scan 확인
- [08-XX 22:15] s2 · WHERE title LIKE 'word%' — Index Scan / LIKE '%word%' — Seq Scan
- [08-XX 22:30] s2 · 묵시적 형변환 — varchar = 숫자 시 Seq Scan
- [08-XX 22:00] s3 · (a, b, c) 복합 — WHERE a — OK / WHERE b — 미사용 확인
- [08-XX 22:15] s3 · Index Only Scan 확인 (Extra)
- [08-XX 23:00] s4 · 7 주차 N+1 SQL EXPLAIN — 회당 plan + 누적 시간
- [08-XX 23:15] s4 · OFFSET 100000 페이징 — Seq Scan + 100010 read
- [08-XX 23:30] s4 · cursor pagination — `WHERE id < ? LIMIT 10` — Index Scan 10 row
```


━━━━━━━━━━━━━━━━━━━━━━━━━━
환경
━━━━━━━━━━━━━━━━━━━━━━━━━━
- Java 21
- **Spring Boot 3.x** + Spring Data JPA + Hibernate 6.x (7 주차와 동일)
- **PostgreSQL 16** (docker compose — 3 주차에서 깔아본 그대로 재활용)
  - **이유** — H2 의 EXPLAIN 은 통계 / cost 정확성 떨어짐. 옵티마이저 동작 학습 가치 X
  - 100 만 row seed 도 PostgreSQL 의 `generate_series` 가 가장 빠름
- 더미 데이터 — **100 만 row** (10 만은 차이 안 보임 / 1000 만은 학습 시간 길어짐)

## build.gradle 추가

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.0'
    id 'io.spring.dependency-management' version '1.1.4'
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

    // PostgreSQL — H2 대신
    runtimeOnly 'org.postgresql:postgresql'
}
```

## docker-compose.yml

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: cs-study-08-pg
    environment:
      POSTGRES_DB: index_study
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - pg_data:/var/lib/postgresql/data

volumes:
  pg_data:
```

## application.properties

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/index_study
spring.datasource.username=postgres
spring.datasource.password=postgres

spring.jpa.hibernate.ddl-auto=create
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# 7 주차에서 익힘
spring.jpa.open-in-view=false
```

> docker compose up -d 로 PostgreSQL 띄우고 진행. 3 주차 학습자는 같은 docker-compose 재활용 가능.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STAGE 진행 가이드
━━━━━━━━━━━━━━━━━━━━━━━━━━

## 예상 학습 시간 (한 주 기준)

| 단계 | 시간 | 비고 |
|---|---|---|
| STAGE 1 (B+Tree + EXPLAIN 손 작성 + 인덱스 비용 측정) | 2 ~ 3 시간 | **화요일까지 (필수)** |
| **STAGE 2 (인덱스 미사용 6 케이스)** ★ | **2 ~ 3 시간** | **목요일까지 (필수)**. 8 주차 가장 중요한 학습 |
| STAGE 3 (복합 + 커버링 인덱스) | 1 ~ 2 시간 | leftmost prefix + Index Only Scan |
| STAGE 4 (7 주차 JPA SQL EXPLAIN — 살짝) | 1 시간 | N+1 / JOIN FETCH / OFFSET 페이징 |
| **합계 (필수)** | **6 ~ 9 시간** | |
| STAGE 5 [여유] (슬로우 쿼리 + 9 주차 캐시 브릿지) | 30 ~ 60 분 | |

**배분**:
- 7 주차 (8 ~ 12 시간) 보다 약간 짧음. SQL / 도구 사용이라 손맛 빠름
- 직장인 (평일 저녁 1.5 시간 × 4 + 주말 3 시간) — 필수 충분
- 학생 (주말 풀타임 1 일) — 필수 + STAGE 5
- 부담스러우면 **STAGE 1 (B+Tree + EXPLAIN) + STAGE 2 (인덱스 미사용 6) 가 면접 최강**

### [화 11:00 — Draft PR 마감 + 겪기 발표] — STAGE 1

> 8 주차는 **STAGE 1 (B+Tree + EXPLAIN 손 작성 + Seq vs Index 측정) 까지 화요일 분량**.

#### ▸ STAGE 1 — B+Tree + EXPLAIN (필수)

**목표**: 100 만 row 에 인덱스 추가 전 / 후 응답 시간 측정 + EXPLAIN 으로 plan 변화 직접 보기.

##### 1-1. 100 만 row seed

```sql
CREATE TABLE post (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    author_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

INSERT INTO post (title, content, author_id, created_at)
SELECT
    'Post #' || i,
    'Content ' || i,
    (i % 1000) + 1,                                  -- 1000 명 작성자
    NOW() - (i || ' seconds')::interval
FROM generate_series(1, 1000000) AS i;

ANALYZE post;                                         -- 통계 갱신 필수
```

**관찰 포인트**:
- 100 만 row INSERT 약 5 ~ 30 초 (환경 따라)
- `\dt+ post` (psql) 로 테이블 크기 확인 — 약 100 ~ 200 MB
- `ANALYZE` 없으면 옵티마이저가 잘못된 plan 선택 가능

##### 1-2. 인덱스 없이 — Seq Scan

```sql
-- ★ LIMIT 10 으로 TOP-N. 인덱스 효과가 극명하게 드러나는 형태.
--   LIMIT 없이 1000 row 다 가져오면 선택도 0.1% 라도 옵티마이저가 Seq 선택 가능.
EXPLAIN ANALYZE
SELECT * FROM post WHERE author_id = 42 ORDER BY created_at DESC LIMIT 10;
```

**예상 출력**:
```
Seq Scan on post  (cost=0.00..28846.00 rows=1000 width=...) (actual time=0.012..150.234 rows=1000 loops=1)
  Filter: (author_id = 42)
  Rows Removed by Filter: 999000
Planning Time: 0.123 ms
Execution Time: 150.350 ms
```

**관찰 포인트**:
- `Seq Scan` — 100 만 row 전체 스캔
- `Rows Removed by Filter` — 99.9 만 row 버림
- `Execution Time` — 100 ~ 300ms (디스크 / 캐시 상태)

##### 1-3. 인덱스 추가 — Index Scan

```sql
CREATE INDEX idx_post_author ON post(author_id);
ANALYZE post;

EXPLAIN ANALYZE
SELECT * FROM post WHERE author_id = 42 ORDER BY created_at DESC LIMIT 10;
```

**예상 출력**:
```
Bitmap Heap Scan on post  (cost=4.55..823.45 rows=1000 width=...) (actual time=0.234..2.456 rows=1000 loops=1)
  Recheck Cond: (author_id = 42)
  Heap Blocks: exact=120
  ->  Bitmap Index Scan on idx_post_author  (cost=0.00..4.30 rows=1000 width=0) (actual time=0.123..0.123 rows=1000 loops=1)
        Index Cond: (author_id = 42)
Planning Time: 0.456 ms
Execution Time: 2.567 ms
```

**관찰 포인트**:
- `Bitmap Index Scan` (PostgreSQL) — 인덱스로 row 위치 찾고 테이블 접근
- `Execution Time` — 150ms → 2.5ms (60 배)
- 더 작은 결과 (1 row) 면 `Index Scan` 으로 떨어짐

##### 1-4. INSERT 비용 — 인덱스 N 개 트레이드오프

```sql
-- (A) 인덱스 0 개 (PK 만) — 독립 테이블 정의로 본 post 인덱스 영향 차단
CREATE TABLE post_a (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200), author_id BIGINT, code VARCHAR(20),
    created_at TIMESTAMP DEFAULT NOW()
);

EXPLAIN ANALYZE
INSERT INTO post_a (title, author_id, code)
SELECT 'X', 1, 'CODE' FROM generate_series(1, 100000);

-- (B) 인덱스 5 개
CREATE TABLE post_b (LIKE post_a INCLUDING DEFAULTS);
CREATE INDEX ON post_b (author_id);
CREATE INDEX ON post_b (created_at);
CREATE INDEX ON post_b (title);
CREATE INDEX ON post_b (code);
CREATE INDEX ON post_b (LOWER(title));

EXPLAIN ANALYZE
INSERT INTO post_b (title, author_id, code)
SELECT 'X', 1, 'CODE' FROM generate_series(1, 100000);
```

**관찰 포인트**:
- 인덱스 0 개: 100 ~ 300ms / 인덱스 5 개: 500 ~ 1500ms (5 배)
- 디스크 사용량도 인덱스 5 개 = 본 테이블 + 인덱스 5 개 만큼
- **트레이드오프** — 조회 빠름 / 변경 느림. 무작정 인덱스 추가 X


### [목 11:00 — Ready PR 전환 + 코드 발표] — STAGE 2 ~ STAGE 4

> STAGE 1 (B+Tree + EXPLAIN 손맛) 은 화요일까지. 목요일까지는 인덱스 미사용 6 케이스 (STAGE 2) → 복합 / 커버링 (STAGE 3) → 7 주차 SQL EXPLAIN (STAGE 4, 살짝).

#### ▸ STAGE 2 — 인덱스 미사용 6 케이스 (필수, **8 주차 가장 중요**)

##### 2-1. 함수 적용 — `WHERE LOWER(name) = ?`

```sql
CREATE INDEX idx_post_title ON post(title);
ANALYZE post;

-- (a) 함수 없이 — Index Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE title = 'Post #42';

-- (b) 함수 적용 — Seq Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE LOWER(title) = 'post #42';
```

**관찰 포인트**:
- (a) Index Scan / (b) Seq Scan — 같은 결과인데 plan 다름
- **해결 1** — 함수 인덱스: `CREATE INDEX ON post(LOWER(title))`
- **해결 2** — 애플리케이션에서 미리 lower 해서 비교 — `WHERE title = 'post #42'` (단 컬럼 자체에 LOWER 저장)
- 시간 비교 — Index Scan 1ms vs Seq Scan 200ms

##### 2-2. LIKE 와일드카드 위치

```sql
-- (a) 'word%' — Index Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE title LIKE 'Post #1%';

-- (b) '%word' — Seq Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE title LIKE '%#1';

-- (c) '%word%' — Seq Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE title LIKE '%Post%';
```

**관찰 포인트**:
- 앞 와일드카드 = B+Tree 의 정렬 활용 불가 → Seq Scan
- **PostgreSQL 의 대안 — `pg_trgm` GIN 인덱스** (`CREATE EXTENSION pg_trgm; CREATE INDEX ON post USING gin (title gin_trgm_ops);`) — `%word%` 양쪽 와일드카드도 인덱스 사용. Elasticsearch 까지 안 쓰고 PostgreSQL 안에서 해결하는 가장 흔한 패턴
- 실무 — 검색량 / 정확도 요구 크면 Elasticsearch 분리. 중간 규모는 pg_trgm 으로 충분

##### 2-3. OR 조건 — 3 단계 비교 (한쪽만 / 양쪽 / UNION)

```sql
CREATE INDEX idx_post_author ON post(author_id);

-- (a) author_id 만 — Index Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE author_id = 42 LIMIT 10;

-- (b1) OR + title (인덱스 없음) — Seq Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE author_id = 42 OR title = 'X';

-- (b2) title 인덱스 추가 — BitmapOr
CREATE INDEX idx_post_title ON post(title);
ANALYZE post;
EXPLAIN ANALYZE SELECT * FROM post WHERE author_id = 42 OR title = 'X';

-- (c) UNION 으로 분리 — 둘 다 Index Scan
EXPLAIN ANALYZE
SELECT * FROM post WHERE author_id = 42
UNION
SELECT * FROM post WHERE title = 'X';
```

**관찰 포인트**:
- (b1) — OR 한쪽 인덱스 없으면 전체 Seq Scan
- (b2) — **양쪽 다 인덱스 있어야 BitmapOr 살아남** (PostgreSQL 의 OR 최적화)
- (c) — UNION 분리는 양쪽 인덱스 독립 활용. 한쪽 인덱스만 있어도 그 쿼리는 Index Scan
- 핵심 — OR 는 양쪽 다 인덱스 있어야. 한쪽만 있으면 UNION 분리 권장

##### 2-4. 묵시적 형변환 — DB 마다 다른 동작

```sql
ALTER TABLE post ADD COLUMN code VARCHAR(20);
UPDATE post SET code = 'CODE-' || id;
CREATE INDEX idx_post_code ON post(code);

-- (a) 같은 타입 — Index Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE code = 'CODE-42';

-- (b) varchar = integer — DB 마다 다름
-- ⚠️ PostgreSQL — 캐스팅 거부 → 에러
--    "ERROR: operator does not exist: character varying = integer"
-- ⚠️ MySQL    — 컬럼을 숫자로 캐스팅 → Seq Scan (전형적 함정)
-- EXPLAIN ANALYZE SELECT * FROM post WHERE code = 42;

-- (c) PostgreSQL 의 핵심 — 상수 쪽 캐스팅은 OK
EXPLAIN ANALYZE SELECT * FROM post WHERE author_id = '42';
-- → integer 컬럼에 '42' 문자열 → 상수 쪽 캐스팅 → 인덱스 정상 사용
```

**관찰 포인트**:
- 핵심 메시지: 묵시적 형변환은 인덱스를 죽이는 함정이지만 **DB 마다 동작이 다르다**
- PostgreSQL — 컬럼 쪽 캐스팅이 필요한 경우만 인덱스 X. 상수 쪽 캐스팅은 OK
- MySQL — 둘 다 함정. 컬럼 캐스팅이 발생하면 인덱스 X
- **해결** — 애플리케이션에서 타입 맞추기 (정확한 타입 전달). JPA / JPQL 은 보통 타입 안전

##### 2-5. NULL 처리

```sql
ALTER TABLE post ADD COLUMN deleted_at TIMESTAMP NULL;
UPDATE post SET deleted_at = NOW() WHERE id % 100 = 0;     -- 1% 삭제 표시
CREATE INDEX idx_post_deleted ON post(deleted_at);

-- (a) IS NULL — PostgreSQL 은 Index Scan 가능
EXPLAIN ANALYZE SELECT * FROM post WHERE deleted_at IS NULL;

-- (b) IS NOT NULL — Seq Scan 가능성 (cardinality 99% 라 옵티마이저 선택)
EXPLAIN ANALYZE SELECT * FROM post WHERE deleted_at IS NOT NULL;

-- 해결 — 부분 인덱스
CREATE INDEX idx_post_active ON post(id) WHERE deleted_at IS NULL;
EXPLAIN ANALYZE SELECT * FROM post WHERE deleted_at IS NULL AND id = 42;
```

**관찰 포인트**:
- PostgreSQL 은 IS NULL 도 인덱스 가능 (MySQL 은 버전에 따라)
- **부분 인덱스** (Partial Index) — 자주 쓰는 조건만 인덱스. 크기 작음 + 빠름
- 소프트 삭제 (`deleted_at`) 패턴에 강함

##### 2-6. 부정형 — `<>`, `NOT IN`

```sql
-- (a) = — Index Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE author_id = 42;

-- (b) <> — Seq Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE author_id <> 42;

-- (c) NOT IN — Seq Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE author_id NOT IN (1, 2, 3);
```

**관찰 포인트**:
- 부정형 = 옵티마이저가 "거의 모든 row 매칭" 판단 → Seq Scan
- 의미상 부정형이 필요하면 — 도메인 재설계 / 캐싱 등 별 방법
- `NOT EXISTS` 도 비슷 (단 작은 서브쿼리는 OK)

##### 2-7. 측정 매트릭스 (100 만 row 기준)

| 케이스 | Plan | Execution Time |
|---|---|---|
| `WHERE col = ?` (인덱스 O) | Index Scan | ~ 1ms |
| `WHERE LOWER(col) = ?` | Seq Scan | ~ 200ms |
| `WHERE col LIKE 'x%'` | Index Scan | ~ 5ms |
| `WHERE col LIKE '%x%'` | Seq Scan | ~ 200ms |
| `WHERE a=? OR b=?` (b 인덱스 X) | Seq Scan | ~ 200ms |
| 묵시적 형변환 | Seq Scan | ~ 200ms |
| `IS NULL` (cardinality 낮음) | Index Scan | ~ 2ms |
| `<>` | Seq Scan | ~ 200ms |


#### ▸ STAGE 3 — 복합 + 커버링 인덱스 (필수)

##### 3-1. 복합 인덱스 + leftmost prefix

```sql
CREATE INDEX idx_post_author_created ON post(author_id, created_at DESC);

-- (a) 첫 컬럼만 — 사용 OK
EXPLAIN ANALYZE SELECT * FROM post WHERE author_id = 42;

-- (b) 첫 + 두 번째 — 사용 OK
EXPLAIN ANALYZE
SELECT * FROM post WHERE author_id = 42 ORDER BY created_at DESC LIMIT 10;

-- (c) 두 번째만 — Seq Scan (leftmost prefix 위반)
EXPLAIN ANALYZE SELECT * FROM post WHERE created_at > NOW() - INTERVAL '1 day';

-- (d) 정렬만 — 사용 가능 / 불가능 케이스
EXPLAIN ANALYZE SELECT * FROM post ORDER BY created_at DESC LIMIT 10;   -- Seq Scan
EXPLAIN ANALYZE SELECT * FROM post WHERE author_id = 42 ORDER BY created_at DESC LIMIT 10;   -- Index Scan
```

**관찰 포인트**:
- 복합 인덱스의 순서 = 사용 가능 패턴 결정
- "자주 같이 쓰는 컬럼 묶기 + 등호 → 범위 순"
- `(author_id, created_at DESC)` 면 ORDER BY 도 인덱스 정렬 활용 → Sort 제거

##### 3-2. 커버링 인덱스 — Index Only Scan

```sql
-- ★ VACUUM 필수 — visibility map 이 최신이어야 Heap Fetches = 0 (진짜 Index Only Scan)
VACUUM post;

-- 모든 SELECT 컬럼이 인덱스 안에 → 테이블 접근 X
EXPLAIN ANALYZE
SELECT author_id, created_at FROM post WHERE author_id = 42 ORDER BY created_at DESC LIMIT 10;

-- PostgreSQL — INCLUDE 절로 명시
CREATE INDEX idx_post_covering ON post(author_id, created_at DESC) INCLUDE (title);

EXPLAIN ANALYZE
SELECT author_id, created_at, title FROM post WHERE author_id = 42 ORDER BY created_at DESC LIMIT 10;
-- → "Index Only Scan"
```

**관찰 포인트**:
- `Index Only Scan` (Extra 또는 plan 첫 줄) — 테이블 접근 0 회
- 효과 — 디스크 IO 절감. 빈도 높은 쿼리에 강함
- 단 인덱스 크기 증가 (INCLUDE 컬럼만큼)

##### 3-3. 복합 인덱스 순서 결정

| 결정 기준 | 우선순위 |
|---|---|
| Cardinality 높은 컬럼 | 1 |
| 등호 (`=`) 조건 컬럼 | 2 |
| 범위 (`>`, `<`, BETWEEN) 컬럼 | 3 |
| ORDER BY 컬럼 | 4 |

**규칙**:
- 등호 조건 컬럼 먼저, 범위 / 정렬 컬럼 뒤
- `WHERE a=? AND b BETWEEN ? AND ? ORDER BY c` → `(a, b, c)` 인덱스
- `WHERE a BETWEEN ? AND ? AND b=?` → `(b, a)` — b 등호 먼저


#### ▸ STAGE 4 — 7 주차 JPA SQL EXPLAIN (필수, **살짝만**)

> ⏰ 8 주차의 메인은 STAGE 1 ~ 3. STAGE 4 는 7 주차 학습을 8 주차 도구로 점검하는 자리. 30 ~ 60 분.

##### 4-1. 7 주차 N+1 의 SQL → EXPLAIN

7 주차 Stage2_1 에서 본 N+1 SQL — `SELECT * FROM comment WHERE post_id = ?` N 회 발행.

```sql
-- 한 회당 plan
EXPLAIN ANALYZE SELECT * FROM comment WHERE post_id = 42;

-- post_id 인덱스 없으면 매 회 Seq Scan → N × Seq Scan = 재앙
-- post_id 인덱스 있어도 N 회 자체의 네트워크 / 파싱 오버헤드는 그대로
```

**관찰 포인트**:
- post_id 인덱스 + N+1 = "회당 빠르나 회수 자체 문제"
- 7 주차 JOIN FETCH / @BatchSize 가 해결한 건 회수 자체

##### 4-2. JOIN FETCH 의 SQL → EXPLAIN

```sql
EXPLAIN ANALYZE
SELECT p.*, c.* FROM post p
LEFT JOIN comment c ON c.post_id = p.id
WHERE p.author_id = 42;
```

**관찰 포인트**:
- Join 알고리즘 — Hash Join / Nested Loop / Merge Join 중 옵티마이저 선택
- 결과 row 수 (N × M) 가 클수록 비싸짐
- author 인덱스 + post_id 인덱스 모두 있어야 빠름

##### 4-3. OFFSET 큰 페이징 함정

```sql
-- (a) OFFSET 작음 — 빠름
EXPLAIN ANALYZE SELECT * FROM post ORDER BY id LIMIT 10 OFFSET 10;

-- (b) OFFSET 큼 — 100010 row 읽고 10 만 반환
EXPLAIN ANALYZE SELECT * FROM post ORDER BY id LIMIT 10 OFFSET 100000;
```

**해결 — cursor pagination**:
```sql
-- 이전 페이지의 마지막 id 를 cursor 로
SELECT * FROM post WHERE id < 100000 ORDER BY id DESC LIMIT 10;
-- Index Scan + LIMIT 10 만 읽음
```

**관찰 포인트**:
- OFFSET 100 = 100 ms / OFFSET 100000 = 5 ~ 50 초 (디스크 / 캐시 따라)
- cursor pagination 은 무한 스크롤에 자연 (이전 마지막 id 전달)
- 단 정렬 컬럼이 unique 해야 (id 같은 PK 권장)


━━━━━━━━━━━━━━━━━━━━━━━━━━
─── 여기 아래는 선택 (시간 여유 시) ───
━━━━━━━━━━━━━━━━━━━━━━━━━━

### [선택] ▸ STAGE 5 — 슬로우 쿼리 발견 + 9 주차 브릿지

##### 5-1. pg_stat_statements

```sql
-- PostgreSQL — 슬로우 쿼리 통계 모듈
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- 가장 느린 쿼리 TOP 10
SELECT query, calls, total_exec_time, mean_exec_time
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 10;
```

##### 5-2. 9 주차 (캐시) 예고

인덱스로 못 풀리는 케이스:
- 같은 쿼리가 초당 수천 회 — DB 가 인덱스로 빠르게 처리해도 부하 누적
- 결과가 자주 안 바뀌는데 매번 DB 가는 게 낭비

→ **캐시** (9 주차). Redis / Caffeine / Spring Cache. JPA 2 차 캐시도.


━━━━━━━━━━━━━━━━━━━━━━━━━━
금지 키워드 — STAGE 1 ~ 2 동안만 (검색 금지)
━━━━━━━━━━━━━━━━━━━━━━━━━━
- QueryDSL — JPQL 타입 안전 빌더. 학습 후 익히기
- 2 차 캐시 / Redis / EhCache — 9 주차 영역
- 전문 검색 (FTS) / Elasticsearch / pg_trgm — 학습 범위 밖
- 파티셔닝 / 샤딩 — DB 운영 영역, 본 학습 후
- B-Tree / B+Tree 의 구체 알고리즘 — 위키 그림 정도만, 깊이는 별
- Hibernate Statistics 의 모든 metric — STAGE 1 후 도구 활용 OK


━━━━━━━━━━━━━━━━━━━━━━━━━━
학습 기록 (자유)
━━━━━━━━━━━━━━━━━━━━━━━━━━

### 7 주차 회상 — 8 주차로 이어지는 지점

| 7 주차에서 본 것 | 8 주차에서 확장 |
|---|---|
| N+1 = 1 + N 회 SQL | 회당 SQL 의 plan + 인덱스 사용 확인 |
| JOIN FETCH = 1 회 SQL | LEFT JOIN 의 plan + join 알고리즘 |
| @BatchSize = IN 절 묶음 | IN 절 길이가 plan 에 미치는 영향 |
| OSIV 의 커넥션 점유 | 슬로우 쿼리 + 커넥션 풀 고갈 직결 |
| JPA 가 만든 SQL | 같은 EXPLAIN 으로 직접 점검 가능 |

### 8 주차 참고 질문 (답하고 싶은 만큼만)
- B+Tree 가 왜 빠른가 — 본인 말로 1 분
- 인덱스 미사용 6 케이스 중 본인이 가장 자주 만난 것
- 복합 인덱스 leftmost prefix 본인 예 1 개
- 커버링 인덱스 — 언제 효과적인가
- 7 주차 N+1 의 회당 SQL plan + 회수 자체의 비용
- OFFSET 큰 페이징 함정 + cursor pagination 본인 답
- 인덱스 추가의 트레이드오프 — 결정 기준
- `ANALYZE` 통계 갱신 안 하면 어떤 일이 일어나나
- 7 주차 JOIN FETCH 의 SQL EXPLAIN 본인 환경에서 어떤 join 알고리즘 선택되나
- 9 주차 캐시가 인덱스로 못 풀리는 영역 — 본인 예상

### 면접 단골 + 본인 답
- **"B+Tree 가 왜 빠른가"** — 높이 3 ~ 4 로 수백만 row 도 3 ~ 4 회 IO. 노드 = 디스크 페이지
- **"인덱스 미사용 6 케이스"** — 함수 / LIKE 와일드카드 / OR / 형변환 / NULL / 부정형
- **"복합 인덱스 leftmost prefix"** — `(a, b, c)` 에서 `a` / `a, b` / `a, b, c` 만 사용
- **"복합 인덱스 컬럼 순서"** — Cardinality 높음 → 등호 → 범위 순
- **"커버링 인덱스"** — SELECT 컬럼 모두 인덱스 안 → Index Only Scan → 테이블 접근 X
- **"인덱스 추가의 트레이드오프"** — 조회 빠름 / 변경 (INSERT/UPDATE/DELETE) 느림 / 디스크
- **"EXPLAIN 의 cost / rows / actual time"** — cost 상대 추정 / rows 예상 행수 / actual time 실제 측정
- **"OFFSET 페이징 함정"** — OFFSET N 은 N 행 읽고 버림. 큰 N 에서 느림. cursor 로 해결
- **"7 주차 N+1 의 인덱스 측면"** — 회당 빠르나 회수 자체 문제. 인덱스로 회수는 안 줄어듦
- **"인덱스 적게 두는 것이 좋은 이유 + 적절한 개수"** — 한 테이블당 5 ~ 10 개. 변경 비용 + 디스크 + 통계 수집
- **"cursor pagination 의 한계"** — 중간 페이지 점프 불가 / PK 외 정렬 시 복합 cursor / 동률 처리. 무한 스크롤 UI 면 OK, 페이지 번호 UI 면 OFFSET + 인덱스
- **"`LIKE '%word%'` 의 PostgreSQL 해결"** — `pg_trgm` 익스텐션 + GIN 인덱스. 중간 규모 검색에서 Elasticsearch 대안
- **"묵시적 형변환 — DB 마다 다른 동작"** — PostgreSQL = 에러 (varchar=int 거부) / MySQL = 컬럼 캐스팅 (Seq Scan). 둘 다 인덱스 못 씀이라는 결론은 같지만 동작은 다름

### 실무 확장 화두 (스터디 토론 / 면접 후속 질문)
- **`ANALYZE` 와 통계 갱신**: PostgreSQL `autovacuum` 이 자동. 큰 데이터 변경 후 즉시 `ANALYZE` 권장
- **부분 인덱스** (Partial Index): `CREATE INDEX ... WHERE deleted_at IS NULL` — 소프트 삭제 패턴에 강함
- **함수 인덱스 / 표현식 인덱스**: `CREATE INDEX ON t (LOWER(name))` — 함수 적용 쿼리에 인덱스 사용 가능
- **`hint` / 옵티마이저 강제**: PostgreSQL 은 hint 거의 없음 (철학). 통계 정확히 + 인덱스 잘 두면 옵티마이저가 알아서
- **Bitmap Scan vs Index Scan**: 결과 행 많으면 Bitmap (페이지 단위 묶음), 적으면 Index Scan
- **Index Range Scan + LIMIT**: TOP-N 쿼리는 LIMIT 만큼만 인덱스 따라가서 효율적
- **WAL / fsync / 인덱스 쓰기 비용**: INSERT 시 인덱스 트리 + WAL 양쪽 write
- **Cluster 인덱스 (MySQL InnoDB) vs Heap 테이블 (PostgreSQL)**: 저장 방식 다름. InnoDB 는 PK 가 클러스터링
- **MVCC 와 dead tuple**: PostgreSQL UPDATE / DELETE 는 새 row + 옛 row 표시. `VACUUM` 으로 정리. dead tuple 많으면 인덱스 효율 떨어짐
- **9 주차 캐시 vs 인덱스**: 인덱스 = DB 쿼리 빠르게. 캐시 = DB 자체 안 가게. 둘은 직교 (orthogonal)
- **cursor pagination 의 한계**: (1) 중간 페이지 (5 페이지로 점프) 불가 — "다음 / 이전" 만 / (2) 정렬 기준이 PK 외 컬럼이면 cursor 가 복합 (`(created_at, id)`) 으로 복잡 / (3) 정렬 컬럼이 unique 아니면 동률 처리 필요. 무한 스크롤 / 무한 피드에는 자연, 페이지 번호 UI 면 OFFSET + 인덱스 보강이 현실
- **pg_trgm + GIN 인덱스**: PostgreSQL `LIKE '%word%'` 의 표준 해결. `pg_trgm` 익스텐션 + `USING gin (col gin_trgm_ops)`. Elasticsearch 까지 안 가는 중간 규모 검색에 강함
- **MySQL vs PostgreSQL — 묵시적 형변환**: PostgreSQL = 거부 (에러) / MySQL = 컬럼 캐스팅 (Seq Scan). DB 마다 옵티마이저 동작 다름이 8 주차 메타 교훈

### 인덱스 선택 매트릭스 (면접 답변 기준)

| 상황 | 인덱스 / 전략 | 이유 |
|---|---|---|
| `WHERE a = ?` 자주 | 단일 인덱스 `(a)` | 가장 흔한 패턴 |
| `WHERE a = ? AND b = ?` | 복합 `(a, b)` | leftmost prefix + Cardinality 순 |
| `WHERE a = ? ORDER BY b` | 복합 `(a, b)` | Sort 제거 |
| SELECT 컬럼 적고 자주 | 커버링 `(a) INCLUDE (col)` | Index Only Scan |
| `WHERE deleted_at IS NULL` | 부분 인덱스 | 크기 작음 + 빠름 |
| `WHERE LOWER(col) = ?` | 함수 인덱스 | 함수 적용 쿼리에 사용 가능 |
| 부정형 (`<>`, `NOT IN`) | 인덱스 의미 없음 | Seq Scan 더 빠름 |
| OFFSET 큰 페이징 | cursor pagination + `(id)` 인덱스 | OFFSET 없애기 |


━━━━━━━━━━━━━━━━━━━━━━━━━━
막힐 때
━━━━━━━━━━━━━━━━━━━━━━━━━━
1. 직접 시도 (`CLAUDE.md` 룰)
2. AI 에 물어보기 — 3 단계 힌트만 받음 (코드 직답 X)
3. 디스코드 `#질문` 채널 — EXPLAIN 결과 + 본인 가설 함께

**Seq Scan 이 자꾸 나옴**:
1. 데이터량 충분한가 — 1 만 미만이면 옵티마이저가 Seq 선호
2. `ANALYZE table` 통계 갱신했는가
3. 인덱스 미사용 6 케이스 중 하나인가 (함수 / LIKE '%x%' / OR / 형변환 / 부정형)
4. PostgreSQL — `SET enable_seqscan = off` 로 강제 인덱스 — 단 디버깅용

**Index Scan 인데 느림**:
1. Index Scan + 큰 Buffers — 결과 row 많은 경우. Bitmap Scan 또는 LIMIT 추가
2. Sort 가 plan 에 있음 — 인덱스 순서 활용 못함. 복합 인덱스 (정렬 컬럼 포함)
3. Index Only Scan 못 가는 SELECT 컬럼 — 커버링 인덱스 검토

**EXPLAIN 의 rows 예상이 실제와 다름**:
1. 통계 부정확 — `ANALYZE` 후 재측정
2. 데이터 분포 skew — 일부 값이 압도적으로 많음. 옵티마이저가 평균 가정 깨짐
3. 함수 / 표현식 — 옵티마이저가 예상 못함. 결과 캐시 또는 추정 hint

**인덱스 추가했는데 안 쓰는 것 같음**:
1. EXPLAIN ANALYZE 로 실제 plan 확인 — `Filter` 와 `Index Cond` 구분
2. 컬럼 순서 / 데이터 타입 일치 확인
3. `ANALYZE table` 안 했을 가능성

**INSERT / UPDATE 가 갑자기 느림**:
1. 인덱스 N 개 추가했는가 — 변경 비용 N 배
2. UPDATE 가 인덱스 컬럼을 자주 변경 → 트리 재구성 비용
3. 대량 INSERT 는 인덱스 drop → 삽입 → 인덱스 재생성이 빠를 수 있음
