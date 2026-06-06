# 8주차 인덱스 + EXPLAIN + 쿼리 튜닝 — 용어 정리

> 7 주차의 JPA 용어 정리와 같은 형식. STAGE 진행 전 또는 학습 중 막힐 때 참조.
>
> 시나리오 단어표 (15 개) 는 핵심만, 이 파일은 카테고리별 전체.

---

## 🌳 인덱스 자료구조

| 용어 | 풀어쓰면 |
|---|---|
| **인덱스** (Index) | 컬럼 값 → row 위치 빠르게 찾는 보조 자료구조 |
| **B-Tree** | Balanced Tree. 모든 리프 노드 깊이 동일 |
| **B+Tree** | B-Tree 변형. 리프 노드만 데이터 + 리프끼리 연결 리스트. 범위 / 정렬 강함. **DB 인덱스 표준** |
| **Hash Index** | 해시 기반. 동등 비교 (`=`) 만. 범위 / 정렬 X. PostgreSQL 거의 안 씀 |
| **GiST / GIN** | PostgreSQL 의 특수 인덱스. 전문 검색 / 다차원 / JSON 등 |
| **Heap Table** | PostgreSQL — 데이터 무순서 저장. PK 도 별도 인덱스 |
| **Cluster Index** | MySQL InnoDB — PK 가 데이터 순서. 데이터 자체가 PK 정렬 상태 |
| **Secondary Index** | PK 외 다른 컬럼 인덱스. InnoDB 는 PK 를 leaf 값으로 |
| **B+Tree 높이** | 노드 fanout (보통 100 ~ 1000) 가 크면 100 만 row 도 높이 3 ~ 4 |
| **Leaf 노드** | 인덱스 트리 의 가장 아래. 실제 row 포인터 또는 데이터 |
| **fanout** | 노드 하나의 자식 수. 클수록 트리 낮음 |

## 🔍 EXPLAIN / 실행 계획

| 용어 | 풀어쓰면 |
|---|---|
| **EXPLAIN** | DB 가 예상 plan 출력. 실제 실행 X |
| **EXPLAIN ANALYZE** | 실제 실행 + 측정. **본격 학습 / 디버깅 도구** |
| **EXPLAIN (ANALYZE, BUFFERS)** | + 페이지 IO 통계. PostgreSQL |
| **Seq Scan** | 순차 스캔 — 테이블 전체 읽기 |
| **Index Scan** | 인덱스 → row 위치 → 테이블 접근 |
| **Index Only Scan** | 인덱스만으로 결과 완성. 테이블 접근 X. **커버링 인덱스** |
| **Bitmap Index Scan** | 인덱스로 row 위치 비트맵 만들고 한 번에 fetch. 결과 많을 때 |
| **Bitmap Heap Scan** | Bitmap Index Scan 이 만든 비트맵으로 테이블 fetch |
| **cost** | 옵티마이저 비용 추정. 단위 무의미 (상대 비교) |
| **rows** | 예상 결과 행 수. 실제와 차이 크면 통계 부정확 |
| **width** | 한 row 평균 byte |
| **actual time** | EXPLAIN ANALYZE 시 실제 시간 (ms) |
| **loops** | 반복 횟수 (Nested Loop join 의 안쪽) |
| **Filter / Index Cond** | Filter = 행을 가져온 뒤 거름 / Index Cond = 인덱스 자체로 거름 |
| **Rows Removed by Filter** | Filter 가 버린 행 수. 크면 인덱스 검토 |

## 📊 통계 + Cardinality

| 용어 | 풀어쓰면 |
|---|---|
| **Cardinality** | 컬럼의 unique 값 수. 높을수록 인덱스 효과 큼 |
| **Selectivity** | 조건이 거르는 비율. 1 / Cardinality 비슷 |
| **고 Cardinality 컬럼** | user_id / email / order_id — 인덱스 가치 ★★★ |
| **저 Cardinality 컬럼** | gender (2) / status (5) — 인덱스 가치 ★ |
| **ANALYZE table** | 통계 수동 갱신. PostgreSQL `ANALYZE` / MySQL `ANALYZE TABLE` |
| **autovacuum** | PostgreSQL 자동 통계 + dead tuple 정리 |
| **pg_stat_statements** | 슬로우 쿼리 추적 모듈 |
| **VACUUM** | dead tuple 정리. UPDATE / DELETE 후 |
| **dead tuple** | MVCC 로 인한 옛 row. 인덱스 효율 떨어뜨림 |
| **MVCC** | Multi-Version Concurrency Control. 트랜잭션 격리. PostgreSQL UPDATE = 새 row + 옛 row 마킹 |

## 🚫 인덱스 미사용 6 케이스

| 용어 | 풀어쓰면 |
|---|---|
| **함수 적용** | `WHERE LOWER(col) = ?` → 인덱스 X. 해결 = 함수 인덱스 |
| **LIKE 앞 와일드카드** | `LIKE '%x'` / `'%x%'` → Seq Scan. `'x%'` 만 OK |
| **`pg_trgm` + GIN** | PostgreSQL — `%word%` 도 인덱스 사용. `CREATE EXTENSION pg_trgm; CREATE INDEX ON t USING gin (col gin_trgm_ops)` |
| **OR 조건** | 한쪽 컬럼 인덱스 없으면 전체 Seq Scan. UNION 분리 또는 양쪽 인덱스 |
| **묵시적 형변환** | **DB 마다 다름**. PostgreSQL = `varchar = int` 거부 (에러) / MySQL = 컬럼 캐스팅 → Seq Scan. 둘 다 인덱스 못 씀이라는 결론은 같음 |
| **NULL 처리** | `IS NULL` / `IS NOT NULL` — DB 별 다름. PostgreSQL 은 IS NULL 인덱스 OK |
| **부정형** | `<>`, `NOT IN`, `NOT EXISTS` → 보통 Seq Scan |

## 🧩 복합 인덱스 + 커버링

| 용어 | 풀어쓰면 |
|---|---|
| **복합 인덱스** | 여러 컬럼 묶은 인덱스. `(a, b, c)` |
| **leftmost prefix** | (a, b, c) → `a` / `a, b` / `a, b, c` 사용 OK. `b` 만 X / `b, c` 만 X |
| **컬럼 순서 결정** | Cardinality 높음 → 등호 → 범위 / 정렬 순 |
| **커버링 인덱스** | SELECT 컬럼이 모두 인덱스 안 → 테이블 접근 X → Index Only Scan |
| **`INCLUDE` 절** | PostgreSQL — 인덱스에 컬럼 추가 (필터링 X, 커버링 용) |
| **부분 인덱스** (Partial Index) | `CREATE INDEX ... WHERE deleted_at IS NULL` — 조건 만족 row 만 |
| **함수 인덱스** (Expression Index) | `CREATE INDEX ON t (LOWER(name))` — 함수 적용 결과로 인덱싱 |
| **unique 인덱스** | 중복 X 보장. PK 도 자동 unique 인덱스 |
| **multi-column 정렬** | `(a, b DESC)` — 인덱스 자체가 정렬 → Sort 제거 |

## ⚡ JOIN 알고리즘 (Plan 에 나옴)

| 용어 | 풀어쓰면 |
|---|---|
| **Nested Loop Join** | 바깥 N + 안쪽 N×M. 작은 데이터 / 인덱스 있는 경우 |
| **Hash Join** | 한쪽으로 해시 테이블 만들고 다른 쪽으로 조회. 큰 데이터에 강함 |
| **Merge Join** | 양쪽 정렬 후 병합. 양쪽 다 정렬되어 있을 때 (인덱스 정렬 등) |
| **옵티마이저의 선택** | 데이터량 / 인덱스 / 통계로 자동 결정. PostgreSQL 은 hint 거의 없음 |

## 📄 페이징 + 정렬

| 용어 | 풀어쓰면 |
|---|---|
| **OFFSET / LIMIT** | 표준 SQL 페이징. OFFSET 큼 → N 행 읽고 버림 |
| **OFFSET 페이징 함정** | LIMIT 10 OFFSET 100000 → 100010 행 읽음. 큰 페이지 느림 |
| **cursor pagination** | `WHERE id > ? ORDER BY id LIMIT 10` — 이전 마지막 id 를 cursor. 빠름. 무한 스크롤 자연 |
| **cursor 의 한계** | (1) 중간 페이지 점프 불가 / (2) PK 외 정렬은 복합 cursor `(col, id)` / (3) 정렬 컬럼 unique 아니면 동률 처리 필요 |
| **keyset pagination** | cursor pagination 의 다른 이름 |
| **정렬 + 인덱스** | `(col DESC)` 인덱스 → ORDER BY 시 Sort 단계 제거 |

## 🌟 7 주차 회수 + 9 주차 브릿지

| 용어 | 풀어쓰면 |
|---|---|
| **N+1 의 회당 plan** | 회당 SQL 자체는 인덱스로 빠르나, 회수 자체 (네트워크 / 파싱) 는 인덱스로 못 줄임 |
| **JOIN FETCH plan** | LEFT OUTER JOIN. Hash Join / Nested Loop 중 옵티마이저 선택 |
| **IN 절 길이와 plan** | `IN (?, ?, ...)` 길이가 plan 에 영향. 매우 길면 옵티마이저 비용 추정 부정확 |
| **인덱스로 못 풀리는 케이스** | 같은 쿼리 초당 수천 회 / 결과 자주 안 바뀜 → 캐시 (9 주차) |
| **인덱스 vs 캐시** | 인덱스 = DB 쿼리 빠르게 / 캐시 = DB 자체 안 가게. 직교 |
| **Spring Cache** | 9 주차 본론 |

## 🧱 학습 도구

| 용어 | 풀어쓰면 |
|---|---|
| **PostgreSQL** | 8 주차 학습 DB. EXPLAIN ANALYZE / 통계 / 옵티마이저 학습 가치 ★★★ |
| **MySQL** | 대안. 옵티마이저 / hint 동작 다름 — 학습 다양성 측면 |
| **psql** | PostgreSQL CLI. `\d table` (구조) / `\di+` (인덱스) / `\timing on` |
| **`generate_series(1, N)`** | PostgreSQL — N 개 row 빠르게 생성. seed 의 표준 |
| **docker-compose** | 3 주차에서 깔아본 PostgreSQL 그대로 재활용 |
| **`SET enable_seqscan = off`** | PostgreSQL — 강제 인덱스. 디버깅 용도만 |
| **`EXPLAIN (FORMAT JSON)`** | plan 을 JSON 으로 — pgAdmin 시각화 도구에 활용 |
| **pgAdmin / DBeaver** | GUI 도구. EXPLAIN 시각화 |

---

## ★ STAGE 1 진입 관문 (입으로 답)

1. **B+Tree 가 빠른 이유** — 높이 3 ~ 4 / 노드 = 디스크 페이지 / 리프 연결 리스트
2. **EXPLAIN 의 Seq Scan / Index Scan / Index Only Scan** — 각각 의미 + 언제 나오는가
3. **인덱스 추가의 트레이드오프** — 조회 빠름 / 변경 느림 / 디스크. 결정 기준

## ★ STAGE 2 진입 관문 (8 주차 가장 중요)

1. **인덱스 미사용 6 케이스** — 함수 / LIKE 앞 와일드카드 / OR / 형변환 / NULL / 부정형
2. **복합 인덱스 leftmost prefix** — `(a, b, c)` 에서 사용 / 미사용 매트릭스
3. **커버링 인덱스** — Index Only Scan 조건 + 효과
