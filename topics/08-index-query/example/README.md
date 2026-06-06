# 8주차 예시 코드 — 게시판 100만 row + EXPLAIN

scenario.md 의 12 개 도메인과 **별개로** 만든 참고 코드입니다.
7 주차 example 의 게시판 도메인 (Post + Comment + Author) 그대로 + 더미 데이터 100 만 / 1000 만 row.

> ⚠️ **베끼지 마시고 본인 도메인으로 변환하세요.** "이런 식으로 흘러가는구나" 감을 잡는 용도.

## 7 주차와 무엇이 같고 다른가

| | 7 주차 JPA | 8 주차 Index / Query |
|---|---|---|
| 도구 | `@Entity` / `@OneToMany` / `@Query` | `EXPLAIN ANALYZE` / `CREATE INDEX` |
| DB | H2 인메모리 | **PostgreSQL** (docker compose) |
| 데이터 규모 | 10 ~ 100 row | **100 만 ~ 1000 만 row** |
| 학습 방법 | Java main 부팅 + SQL 로그 | SQL 직접 실행 + EXPLAIN plan |
| 본질 | "SQL 안 쓰는데 어떻게 INSERT/UPDATE 자동" | "그 SQL 이 실제로 어떻게 실행되는가" |
| 면접 직결 | 영속성 컨텍스트 / N+1 / Lazy 함정 | 인덱스 미사용 6 케이스 / 복합 인덱스 / EXPLAIN |

핵심: 8 주차는 **DB 자체와 마주하는 자리**. Spring / JPA 가 들고 있던 SQL 을 꺼내서 옵티마이저 plan 까지 점검.

## 폴더 구조

```
example/
├── README.md                          # 지금 이 파일
├── docker-compose.yml                 # PostgreSQL 16
└── sql/
    ├── 00_setup.sql                   # 100 만 post + 1000 만 comment seed
    ├── 01_stage1_basics.sql           # Seq Scan vs Index Scan + INSERT 비용
    ├── 02_stage2_unused.sql           # 인덱스 미사용 6 케이스 ★
    ├── 03_stage3_composite.sql        # 복합 + 커버링 인덱스
    └── 04_stage4_jpa.sql              # 7 주차 SQL EXPLAIN (살짝)
```

## 실행 방법

### 1. PostgreSQL 띄우기

```bash
cd topics/08-index-query/example
docker compose up -d

# 잘 떴는지 확인
docker compose ps
```

3 주차에서 다른 PostgreSQL 컨테이너 띄워뒀다면 포트 (5432) 충돌. `docker compose down` 으로 정리 후 진행.

### 2. seed (00_setup.sql) — 약 1 ~ 2 분

```bash
# psql 로 접속 (Docker 컨테이너 안에서)
docker exec -it cs-study-08-pg psql -U postgres -d index_study

# 또는 호스트에 psql 설치되어 있으면
psql -h localhost -U postgres -d index_study

# 안에서 SQL 파일 실행
\i sql/00_setup.sql

# 또는 한 줄로
docker exec -i cs-study-08-pg psql -U postgres -d index_study < sql/00_setup.sql
```

### 3. STAGE 별 실행

```bash
# STAGE 1 — B+Tree + EXPLAIN
docker exec -i cs-study-08-pg psql -U postgres -d index_study < sql/01_stage1_basics.sql

# STAGE 2 — 인덱스 미사용 6 케이스 ★
docker exec -i cs-study-08-pg psql -U postgres -d index_study < sql/02_stage2_unused.sql

# STAGE 3 — 복합 + 커버링
docker exec -i cs-study-08-pg psql -U postgres -d index_study < sql/03_stage3_composite.sql

# STAGE 4 — 7 주차 SQL EXPLAIN (살짝)
docker exec -i cs-study-08-pg psql -U postgres -d index_study < sql/04_stage4_jpa.sql
```

### 4. 측정 결과 기록

각 stage 실행 후 EXPLAIN ANALYZE 의 `Execution Time` 을 [measurements.md](measurements.md) 에 채우기.

## 학습 흐름

1. **STAGE 1** — 인덱스 추가 전 / 후 같은 쿼리 시간 비교. 60 배 정도 차이.
2. **STAGE 2** ★ — 인덱스 있어도 안 먹는 6 케이스. **8 주차 가장 중요한 학습**
3. **STAGE 3** — 복합 인덱스 leftmost prefix + 커버링 인덱스 (Index Only Scan)
4. **STAGE 4** — 7 주차 N+1 / JOIN FETCH / OFFSET 페이징의 plan 점검 (살짝)

## GUI 도구 (선택)

EXPLAIN plan 시각화 / 비교에 GUI 가 편함:
- **DBeaver** — 무료. EXPLAIN 시각화 지원
- **pgAdmin 4** — PostgreSQL 공식 GUI
- **`EXPLAIN (FORMAT JSON)`** + https://explain.depesz.com — 웹 분석기

## 끝나면 docker compose down

```bash
docker compose down -v   # 볼륨까지 삭제 (100 만 row 정리)
```
