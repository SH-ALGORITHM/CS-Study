# 2주차 — 트랜잭션을 붙였는데 왜 아직도 틀려?

이번 주제: 1주차에서 본 race condition 을 **DB 컬럼**으로 옮긴다.
`@Transactional` 마법은 아직 쓰지 않는다 — `BEGIN` / `COMMIT` / 격리 수준을 손으로 다룬다.
도메인은 2주차 학습 포인트 (Lost Update / Phantom Read / 데드락) 에 맞춰 **새로 1 개 선택** (STEP 1 참조).

---

## 우선 알아둬야 할 단어 (시작 전 1분)

| 단어 | 풀어쓰면 |
|---|---|
| **트랜잭션** | "여러 SQL 을 하나의 단위로 묶음" — `BEGIN` ~ `COMMIT` 사이 |
| **격리 수준 (isolation level)** | 동시에 도는 트랜잭션끼리 서로의 변경을 어디까지 보여줄지 결정 |
| **Dirty Read** | 다른 트랜잭션이 **커밋 안 한** 값을 읽음 |
| **Non-repeatable Read** | 같은 트랜잭션 안에서 같은 row 를 두 번 읽었는데 값이 다름 |
| **Phantom Read** | 같은 조건으로 두 번 SELECT 했는데 row 개수가 다름 |
| **Lost Update** | 두 트랜잭션이 같은 row 를 동시에 갱신해서 한쪽 변경이 사라짐 |


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 0. 개념 숙지 (도메인 무관, 모두 동일)
━━━━━━━━━━━━━━━━━━━━━━━━━━

## 필수 개념 (AI 와 대화하며 숙지)
1. 트랜잭션의 ACID
2. 격리 수준 4 단계 — `READ UNCOMMITTED` / `READ COMMITTED` / `REPEATABLE READ` / `SERIALIZABLE`
3. 격리 수준별로 막히는/허용되는 이상 현상 매트릭스
4. PostgreSQL 의 격리 수준 기본값 (`READ COMMITTED`) 과 MySQL 기본값 (`REPEATABLE READ`) 차이
5. 격리 수준은 **커넥션 단위 설정** (JVM 전역 X)

## 자기 검증 (입으로 답할 수 있어야 STAGE 1 시작)
- [ ] 트랜잭션이 없으면 무엇이 문제인지
- [ ] `BEGIN` / `COMMIT` / `ROLLBACK` 각각 언제 일어나는지
- [ ] Dirty Read 와 Non-repeatable Read 차이
- [ ] Non-repeatable Read 와 Phantom Read 차이
- [ ] Lost Update 가 격리 수준만으로 막히는지 (정답: 격리 수준에 따라 다름)
- [ ] 1주차의 메모리 race 와 이번 주차의 DB race 가 무엇이 같고 무엇이 다른지
- [ ] 커넥션 풀에서 꺼낸 커넥션의 `autoCommit` 기본값이 무엇인지 (HikariCP 기본 `true` — `setAutoCommit(false)` 안 부르면 UPDATE 가 즉시 커밋되어 "트랜잭션 안 쓴 것" 과 동일)


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 1. 본인 도메인 선택 — 2주차에 맞게 새로 고른다
━━━━━━━━━━━━━━━━━━━━━━━━━━

2주차 학습 포인트 (**Lost Update / Phantom Read / 데드락**) 를 잘 드러내는 도메인이 따로 있다. 1주차 도메인을 그대로 쓰지 말고 아래에서 다시 1 개 선택.

## 후보 도메인 + 적합도 (9 개 — 7 명이 1 개씩 + 여유 2)

| # | 도메인 | Lost Update | Phantom Read | 데드락 | 패턴 / 메모 |
|---|---|---|---|---|---|
| 1 | **계좌 이체** (`account_transfer`) | ★★★ | ★ | ★★★ | RMW + 두 row 동시 갱신. 가장 고전적 |
| 2 | **환전 / 통화 거래** (`currency_exchange`) | ★★★ | ★ | ★★★ | RMW + 두 통화 row + 환율 곱셈. 1 번과 매커니즘 같지만 도메인 색깔 다름 |
| 3 | **재고 입출고 (다중 창고)** (`inventory`) | ★★★ | ★ | ★★ | RMW + 창고/상품 매트릭스 |
| 4 | **장바구니 결제** (`checkout`) | ★★ | ★★ | ★★★ | 재고 + 포인트 두 테이블 동시 갱신. 다중 테이블 데드락 학습 강함 |
| 5 | **콘서트 좌석 예약** (`seat_reservation`) | ★★★ | ★★ | ★★ | RMW + 좌석 2 개 동시 시 데드락. **셋 다 균형 잡힘** |
| 6 | **회의실 / 스터디룸 예약** (`meeting_room_booking`) | ★★ | ★★★ | ★ | 시간대 범위 검사 → INSERT. Phantom Read 의 정석 |
| 7 | **호텔 객실 예약** (`hotel_booking`) | ★★ | ★★★ | ★★ | 시간대 + 객실 매트릭스. 회의실 + 데드락 |
| 8 | **택시 배차 / 배달 매칭** (`dispatch_match`) | ★★ | ★★★ | ★ | "비어있는 기사 검색" → 매칭. Phantom 자연 |
| 9 | **포인트 적립/차감** (`user_point`) | ★★★ | ★ | ★ | RMW 단순 — **입문 멤버 추천** |

> **RMW** = read-modify-write. SELECT 로 현재 값 읽고 → 앱에서 계산 → UPDATE 로 절대값 세팅하는 흐름.
> 1주차 `count++` 가 망가진 것과 정확히 같은 구조 — 이 패턴이 자연스러운 도메인이 2주차 학습에 강함.

## 도메인별 예상 시나리오 한 줄 (선택할 때 감 잡는 용도)

| # | 한 줄 시나리오 |
|---|---|
| 1 | A 와 B 가 같은 계좌에서 거의 동시에 출금 → 둘 다 잔고 충분으로 보고 잔고 차감 → 한쪽 변경이 사라짐 (Lost Update). 두 계좌를 반대 순서로 이체하면 데드락 |
| 2 | A 가 KRW→USD 환전, B 가 USD→KRW 환전 → 두 통화 row 를 다른 순서로 잡으면 데드락. 환율 곱셈 결과를 RMW 로 쓰면 Lost Update |
| 3 | 창고 A 에서 출고, 창고 B 에서 입고가 동시 발생 → 두 창고 row 갱신 순서 다르면 데드락. 같은 창고 동시 출고 시 RMW 로 Lost Update |
| 4 | 결제 1 은 재고→포인트 순서, 결제 2 는 포인트→재고 순서로 잡음 → 데드락. 두 사용자가 마지막 1 개 동시 결제 시 한쪽이 음수 |
| 5 | A 와 B 가 같은 좌석을 거의 동시에 예약 시도 → 둘 다 "비어있음" 으로 보고 본인 ID 를 예약자로 기록 → 한쪽이 덮어씀 (Lost Update) |
| 6 | B 가 11:00~12:00 회의실 1 비어있는지 SELECT, A 가 같은 시간대 INSERT + COMMIT, B 가 같은 SELECT 다시 → 결과 달라짐 (Phantom Read). 그대로 INSERT 하면 시간대 충돌 |
| 7 | 5/8~5/9 객실 101 비어있는지 두 사용자가 동시에 SELECT → 둘 다 비어있음 → 둘 다 INSERT → 같은 날짜 이중 예약 |
| 8 | 비어있는 기사 1 명을 두 콜이 동시에 SELECT → 둘 다 같은 기사를 잡으려 UPDATE → 한쪽 콜의 배차 정보가 덮어써짐 |
| 9 | A 가 1000 적립, B 가 500 차감을 동시 시도 → 둘 다 같은 잔고를 읽고 본인 계산 결과로 UPDATE → 한쪽 변경 사라짐 |

## 학습자 프로필별 추천

- **2 주차 처음 / 단순 집중**: 9 번 포인트 잔액 — RMW 패턴만 깊게
- **균형형 (LU + Phantom + 데드락 다)**: 5 번 콘서트 좌석 예약, 7 번 호텔 객실 예약
- **데드락 깊게**: 1 번 계좌 이체, 2 번 환전, 4 번 장바구니 결제
- **Phantom Read 깊게**: 6 번 회의실 예약, 8 번 택시 배차

> 선착순으로 잡지 말고 디스코드 `#w02-도메인` 에서 7 명이 의견 맞춰 1 인 1 도메인. 같은 카테고리(예: 1 ↔ 2, 6 ↔ 7) 는 한 명만 잡는 걸 권장 — 측정 결과 비교가 더 흥미로움.

## 1주차 도메인이 약한 이유

- `LikeCounter (count++)` 는 SQL 한 줄 (`UPDATE SET count = count + 1`) 로 끝나서 PG row-lock 이 자동으로 막음 → Lost Update 가 잘 안 보임
- `Attendance (checked_in flip)` 은 단일 boolean → `UPDATE WHERE checked_in = false` 한 줄로 처리되어 학습 포인트 약함

→ 2주차 도메인은 **수치를 읽어 계산해서 다시 쓰는** 흐름이 도메인적으로 정당화되는 것을 고른다.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 2. DB 테이블 매핑
━━━━━━━━━━━━━━━━━━━━━━━━━━

| 도메인 | 테이블 | 핵심 컬럼 | 동작 SQL (read-modify-write 형태) |
|---|---|---|---|
| 1 계좌 이체 | `account` | `id BIGINT, balance NUMERIC` | `SELECT balance FROM account WHERE id = ?` → 앱에서 출금/입금 계산 → `UPDATE account SET balance = ? WHERE id = ?` (출금/입금 두 row) |
| 2 환전 | `wallet` | `user_id, currency, balance NUMERIC` | `SELECT balance FROM wallet WHERE user_id = ? AND currency IN ('KRW','USD')` → 앱에서 환율 곱해 계산 → `UPDATE wallet SET balance = ? WHERE user_id = ? AND currency = ?` (두 통화 row) |
| 3 재고 입출고 | `inventory` | `warehouse_id, item_id, quantity INT` (PK: 두 컬럼) | 권장 시작 데이터: 창고 2 (`A`, `B`) × 상품 1 (`item-1`) = row 2 개. `SELECT quantity FROM inventory WHERE warehouse_id = ? AND item_id = ?` → 앱에서 차감/증가 계산 → `UPDATE ... SET quantity = ?`. 데드락은 두 창고를 다른 순서로 갱신할 때 발생 |
| 4 장바구니 결제 | `stock`, `user_point` | (두 테이블) | 트랜잭션 1 개 안에서 순서대로: ① `SELECT stock FROM stock WHERE item_id = ?` ② `SELECT balance FROM user_point WHERE user_id = ?` ③ 앱에서 재고 ≥ 1 / 잔액 ≥ 가격 검증 ④ `UPDATE stock SET stock = ?` ⑤ `UPDATE user_point SET balance = ?`. 데드락은 두 결제가 ④↔⑤ 순서를 다르게 잡을 때 발생 |
| 5 콘서트 좌석 예약 | `seat` | `concert_id, seat_no, reserved_by BIGINT NULL` | **이 도메인은 "특정 좌석 예약" 패턴으로 통일** (좌석 번호 미리 정함). `SELECT reserved_by FROM seat WHERE concert_id = ? AND seat_no = ?` → null 이면 `UPDATE seat SET reserved_by = ?`. 시작 데이터: 좌석 100 개 (`A1` ~ `A100`) 모두 `NULL` |
| 6 회의실 예약 | `meeting_room_booking` | `room_id, start_at, end_at TIMESTAMPTZ` | `SELECT 1 FROM meeting_room_booking WHERE room_id = ? AND tstzrange(start_at, end_at) && tstzrange(?, ?)` → 비어있으면 `INSERT`. ↓ PG 범위 타입 박스 참조 |
| 7 호텔 객실 예약 | `room_booking` | `room_no, check_in DATE, check_out DATE` | `SELECT 1 FROM room_booking WHERE room_no = ? AND daterange(check_in, check_out) && daterange(?, ?)` → 비어있으면 `INSERT`. ↓ PG 범위 타입 박스 참조 |
| 8 택시 배차 | `driver`, `dispatch` | `driver.status='IDLE'`, `dispatch.driver_id` | `SELECT id FROM driver WHERE status = 'IDLE' LIMIT 1` → `UPDATE driver SET status = 'BUSY' WHERE id = ?` → `INSERT INTO dispatch ...` |
| 9 포인트 잔액 | `user_point` | `user_id, balance INT` | `SELECT balance FROM user_point WHERE user_id = ?` → 앱에서 적립/차감 계산 → `UPDATE user_point SET balance = ?` |

각자 본인 도메인의 `schema.sql` 작성 — 초기 row 와 시작값 (`balance = 10000` 등) 세팅.

> ⚠️ **중요**: 동작 SQL 을 **반드시 read-modify-write 형태로 짤 것.** `UPDATE ... SET balance = balance - ?` 같은 atomic UPDATE 로 짜면 PG row-lock 이 자동으로 막아 버려서 2주차 학습 포인트인 Lost Update 가 보이지 않는다 (이 차이는 STAGE 1 에서 직접 비교).

## PG 범위 타입 짧은 설명 (6, 7 번 도메인 선택자만)

PostgreSQL 의 시간/날짜 범위 검사 문법:

| 문법 | 의미 |
|---|---|
| `tstzrange(a, b)` | 시작 `a` ~ 끝 `b` 의 timestamp with timezone 범위. 기본 `[a, b)` (시작 포함, 끝 불포함) |
| `daterange(a, b)` | 같은 형태의 date 범위 |
| `r1 && r2` | 두 범위가 **겹치는지** 검사 — 겹치면 true |

```sql
-- 예: room_id = 1 에 11:00~12:00 예약이 있는지 확인
SELECT 1 FROM meeting_room_booking
WHERE room_id = 1
  AND tstzrange(start_at, end_at) && tstzrange('2026-05-08 11:00+09', '2026-05-08 12:00+09');
```

> 회의실/호텔 도메인의 Phantom Read 는 이 `&&` 검사 SELECT 와 INSERT 사이에 다른 트랜잭션이 INSERT 해 버리는 패턴.

## measurements.md 형식 (1주차와 일관)

1주차 자동 누적 형식 그대로:
```
- [05-15 14:00] s1 · isolation=READ_COMMITTED, lost_update=N (관찰)
- [05-15 14:30] s2 · jdbc 자동화, isolation=RR, 누락 X / Yms
- [05-16 22:00] s3 · isolation=SERIALIZABLE, 누락 0 / TPS X
```

STAGE 1 의 격리 수준 비교 표는 별도 섹션 (`## STAGE 1 — 격리 수준별 이상 현상`) 으로 분리 — 자동 누적 줄과 표가 한 파일 안에 공존.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 3. 동시에 여러 명 흉내내기 (모두 동일)
━━━━━━━━━━━━━━━━━━━━━━━━━━

1주차의 `ExecutorService` 패턴 그대로. 다른 점: 람다 안에서 **JDBC 트랜잭션을 직접 다룬다.**

> 📌 이 코드 모양은 **STAGE 2-1 자동화 단계**에서 사용. STAGE 1 (psql 손으로) 에서는 안 씀.

```java
ExecutorService executor = Executors.newFixedThreadPool(50);
AtomicInteger successCount = new AtomicInteger(0);   // ⚠️ 결과 세는 도구일 뿐

for (int i = 0; i < 200; i++) {
    executor.submit(() -> {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            // 본인 도메인의 UPDATE 한 줄
            // 결과에 따라 successCount.incrementAndGet()
            conn.commit();
        } catch (SQLException e) {
            // rollback / 로그
        }
    });
}
executor.shutdown();
executor.awaitTermination(30, TimeUnit.SECONDS);
```


━━━━━━━━━━━━━━━━━━━━━━━━━━
환경
━━━━━━━━━━━━━━━━━━━━━━━━━━
- Java 21
- **Spring Boot 안 띄움** — `main()` 메서드 + 순수 JDBC + HikariCP
- DB: **PostgreSQL 16** — 루트 `docker-compose.yml` 의 기존 컨테이너 그대로 사용
- 동시성 흉내: 1주차와 동일한 `ExecutorService`

## DB 띄우기

```bash
# 프로젝트 루트에서
docker compose up -d         # postgres + redis 기동
docker compose down          # 종료
docker compose down -v       # 데이터 볼륨까지 삭제 (DB 완전 초기화 — 측정 사이 깨끗한 상태가 필요할 때)
```

## DB 접속 정보 (루트 `docker-compose.yml` 기준)

| 항목 | 값 |
|---|---|
| host / port | `localhost:5433` (컨테이너 내부는 5432, 호스트는 5433 으로 매핑 — wefin 등 다른 postgres 와 충돌 회피) |
| database | `csstudy` |
| user | `csstudy` |
| password | `csstudy1234` |
| JDBC URL | `jdbc:postgresql://localhost:5433/csstudy` |

```bash
# 접속 확인
docker exec csstudy-postgres psql -U csstudy -d csstudy -c "SELECT 1"
```

## 본인 도메인 스키마

각자 자기 PC 에서 docker 띄우므로 DB 는 본인 전용. 본인 도메인 테이블은 `csstudy` DB 에 그대로 만든다.

```sql
-- 예: 계좌 이체 도메인
CREATE TABLE IF NOT EXISTS account (
    id BIGINT PRIMARY KEY,
    balance NUMERIC NOT NULL
);
INSERT INTO account (id, balance) VALUES (1, 10000), (2, 10000)
ON CONFLICT (id) DO UPDATE SET balance = EXCLUDED.balance;
```

> 측정 시작 전 매번 `TRUNCATE` + `INSERT` 로 초기 상태 복원 (STAGE 3 측정 원칙 참조).
> 도메인 / 측정 셋업이 더 꼬이면 `docker compose down -v` 로 볼륨까지 날리고 다시 시작.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STAGE 진행 가이드
━━━━━━━━━━━━━━━━━━━━━━━━━━

### [월 11:00 — Draft PR 마감 + 겪기 발표]

#### ▸ STAGE 1 — 두 세션으로 손으로 보기 (필수)

**목표**: 격리 수준 4 단계에서 이상 현상이 어떻게 보이고 안 보이는지 **두 눈으로 직접** 확인.

DBeaver / IntelliJ DB / `psql` 중 편한 도구로 **세션 2 개** 동시에 띄우기. 한 창은 세션 A, 다른 창은 세션 B.

> 💡 **두 세션이 진짜 다른 커넥션인지 먼저 확인**. 같은 창에서 두 BEGIN 을 치면 두 트랜잭션이 아니라 하나로 묶여 이상 현상 재현이 안 됨.
> - psql: 각 창에서 `\conninfo` 또는 `SELECT pg_backend_pid();` — PID 가 달라야 정상
> - DBeaver: 좌측 트리에서 Connection 이 두 개 생성됐는지, 각 SQL 에디터의 `Active connection` 이 다른지 확인
> - IntelliJ Database: 각 콘솔이 별도 세션으로 열렸는지 (`Session` 드롭다운에서 새 세션)

##### 1-1. 4 가지 이상 현상 재현

| 이상 현상 | 시나리오 |
|---|---|
| **Dirty Read** | A: `BEGIN; UPDATE account SET balance = 5000 WHERE id = 1;` (commit 안 함) → B: `SELECT balance FROM account WHERE id = 1;` |
| **Non-repeatable Read** | B: `BEGIN; SELECT ...;` → A: `BEGIN; UPDATE ...; COMMIT;` → B: 같은 `SELECT` 다시 |
| **Phantom Read** | B: `BEGIN; SELECT COUNT(*) ...;` → A: `BEGIN; INSERT ...; COMMIT;` → B: 같은 `COUNT(*)` 다시 |
| **Lost Update — atomic UPDATE** | A, B 동시에 `UPDATE ... SET x = x - 1` (한 문장) — 아래 시퀀스 |
| **Lost Update — read-modify-write** | A, B 가 `SELECT` 후 앱에서 계산 → `UPDATE ... SET x = ?` 로 절대값 세팅 — 아래 시퀀스 |

##### Lost Update 의 두 패턴 — 직접 비교

PG `READ COMMITTED` 에서 두 패턴이 결과가 다르다. 본인 눈으로 확인 (예시는 `account.balance` 기준 — 본인 도메인 컬럼으로 치환):

**패턴 1 — atomic UPDATE (한 문장)**
```sql
-- 세션 A, B 둘 다 시작값 10000 인 row 에서:
A: BEGIN;
B: BEGIN;
A: UPDATE account SET balance = balance - 1000 WHERE id = 1;
B: UPDATE account SET balance = balance - 1000 WHERE id = 1;  -- A 가 row 잡고 있어 대기
A: COMMIT;  -- 9000 commit, B 의 대기 풀림
                                                              -- B 가 다시 읽어서 9000 - 1000 = 8000
B: COMMIT;
-- 최종값: 8000 (정답)
```
→ **PG 의 row-level lock 이 자동으로 줄 세움.** Lost Update 안 보임.

**패턴 2 — read-modify-write (SELECT → 앱 계산 → UPDATE 절대값)**
```sql
A: BEGIN;
B: BEGIN;
A: SELECT balance FROM account WHERE id = 1;  -- 결과: 10000
B: SELECT balance FROM account WHERE id = 1;  -- 결과: 10000
-- 두 세션 다 앱 메모리에서 10000 - 1000 = 9000 계산
A: UPDATE account SET balance = 9000 WHERE id = 1;
A: COMMIT;
B: UPDATE account SET balance = 9000 WHERE id = 1;  -- A 변경 덮어씀
B: COMMIT;
-- 최종값: 9000 (두 번 출금했는데 9000, 정답은 8000 — Lost Update 발생)
```
→ **격리 수준이 보호 못 함.** `READ COMMITTED` 에서 명백히 보임.

> ⚠️ 1주차 `count++` 가 read-modify-write 라서 망가진 것과 **정확히 같은 구조.** SELECT 와 UPDATE 사이에 다른 트랜잭션이 끼어든다. 1주차 → 2주차 사다리의 핵심 연결점.

각 시나리오를 4 가지 격리 수준에서 반복:

```sql
SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
```

> 💡 SET 직후 **실제 적용 여부 확인**: `SHOW TRANSACTION ISOLATION LEVEL;` — `SET` 은 현재 트랜잭션에만 적용되므로 BEGIN 후에 SET 해야 함.

##### 1-2. PostgreSQL 만의 동작 관찰 포인트

직접 보기 전에는 짐작하지 말 것. 본인 눈으로 본 결과를 측정 로그에 적기:

- `READ UNCOMMITTED` 에서 Dirty Read 가 **실제로 보이는지** (PG 문서: PG 는 `READ UNCOMMITTED` 를 `READ COMMITTED` 와 동일하게 처리)
- `REPEATABLE READ` 에서 Lost Update 시도 시 **두 번째 트랜잭션이 어떻게 끝나는지** (PG 는 `ERROR: could not serialize access due to concurrent update` 를 던짐 — "막아주긴 하는데 에러로")
- `REPEATABLE READ` 에서 Phantom Read 가 보이는지 (MySQL 과 다른 결과)

##### 1-3. 락 / 대기 상태 직접 보기

세션 A 가 트랜잭션 안에서 UPDATE 하고 commit 안 한 상태에서, 세션 B 가 같은 row 를 UPDATE 하면 무엇이 일어나는지:

```sql
-- 세션 C (관찰용) 에서:
SELECT pid, query, state, wait_event_type, wait_event
FROM pg_stat_activity
WHERE state != 'idle';

SELECT locktype, relation::regclass, mode, granted
FROM pg_locks
WHERE NOT granted OR mode LIKE '%Exclusive%';
```

##### 결과물 — `measurements.md` 에 기록

```
## STAGE 1 — 격리 수준별 이상 현상 (직접 관찰)

| 격리 수준 | Dirty Read | Non-repeatable | Phantom | Lost Update 시도 결과 |
|---|---|---|---|---|
| READ UNCOMMITTED | (관찰) | (관찰) | (관찰) | (관찰) |
| READ COMMITTED | ... | ... | ... | ... |
| REPEATABLE READ | ... | ... | ... | ... |
| SERIALIZABLE | ... | ... | ... | ... |

관찰 노트:
- (예) READ UNCOMMITTED 에서 Dirty Read 가 안 보였다 — PG 문서 X 확인
- (예) REPEATABLE READ 에서 Lost Update 시 ERROR: could not serialize ...
```

> 이 시점부터 STAGE 3 시작 전까지 **금지 키워드 적용** (아래 참조).


### [목 11:00 — Ready PR 전환 + 코드 발표] — STAGE 2 + STAGE 3 모두 이 마감 안에

#### ▸ STAGE 2 — Java 코드로 자동화 (필수)

손으로 본 현상을 N 스레드로 재현. **두 단계로 쪼갠다.**

##### STAGE 2-1. 트랜잭션을 손으로 — 헬퍼 만들지 말 것

- 람다 안에서 `conn.setAutoCommit(false)` / `conn.setTransactionIsolation(...)` / `conn.commit()` / `conn.rollback()` / `try-catch (SQLException)` 전부 손으로 작성
- 같은 try-catch 패턴이 5 번 반복돼도 **그대로 둘 것**
- 목표: 격리 수준 4 단계에서 1000 번 시도 → Lost Update 발생 횟수 측정

##### STAGE 2-2. 반복이 보이면 — 본인이 헬퍼로 추출

- 2-1 에서 같은 try-catch 가 반복되는 걸 본 후, **본인이** 헬퍼로 추출
- 추출하면서 깨달을 것: "이 헬퍼가 4 주차 `@Transactional` 이 자동화하는 일이구나"
- 헬퍼 설계 시 짚을 것:
  - `Runnable` 은 checked exception 못 던짐 — `SQLException` 어떻게 처리?
  - `finally` 에서 `setAutoCommit(true)` (또는 원래 값) 복원? 안 하면 커넥션 풀에 어떤 영향?
  - 트랜잭션 시작 시점의 격리 수준 / 종료 시점의 정리

> **힌트 (막혔을 때만 열기):** `SQLException` 은 checked exception 이라 `Runnable` 로는 못 받음. 두 갈래 — (1) checked exception 을 던질 수 있는 함수형 인터페이스를 직접 정의, (2) `RuntimeException` 으로 감싸기. 둘 다 시도해보고 차이 정리. 그래도 막히면 디스코드 `#질문`.

> **2-1 단계를 건너뛰고 헬퍼부터 쓰지 말 것.** 손으로 5 번 반복한 후에 추출해야 헬퍼의 의미가 보인다. 4 주차에서 `@Transactional` 만났을 때 "내가 만든 헬퍼의 강화판" 으로 인식하는 게 학습 자산이다.


#### ▸ STAGE 3 — 측정 + 해결 (필수)

**※ 이 단계부터 금지 키워드 일부 해제** — 격리 수준 변경 (`SET TRANSACTION ...`) 사용 가능.
단 락 관련 키워드 (`SELECT FOR UPDATE`, `@Version`, 비관적/낙관적 락) 는 **3 주차 보호** 로 계속 금지.

##### 3-1. 격리 수준별 측정 (10 / 50 / 100 / 1000 스레드)

측정 항목 (모두 measurements.md 에 자동 누적):

| 항목 | 의미 |
|---|---|
| **누락** (Lost Update) | 성공 카운트 vs 실제 잔고 차이 (RC 에서 발생, RR/SR 에선 0 근처) |
| **실패** | 트랜잭션 거부 횟수 (RR/SR 의 SQLState 40001 — `serialization_failure`) |
| **응답시간** | 200 시도 전체 끝나는 데 걸린 시간 (5 회 평균) |

`MeasurementLog.save(stage, method, misses, failed, millis)` 시그니처가 위 3 항목 모두 받음. example 의 `Stage3Measurement` 코드 참고.

격리 수준별 예상:

| 격리 수준 | 누락 | 실패 | 응답 |
|---|---|---|---|
| READ_COMMITTED | **많음** (RMW 못 막음) | 0 | 빠름 ~ 중간 |
| REPEATABLE_READ | 0 근처 | **많음** (40001) | 빠름 (실패가 많아 진짜 작업 안 함) |
| SERIALIZABLE | 0 | **더 많음** | 빠름 (RR 과 비슷) |

> ⚠️ 응답시간 역설: RR/SR 이 RC 보다 빨라 보이는 건 **트랜잭션 대부분이 빠르게 실패** 하기 때문. 재시도까지 포함하면 진짜 응답시간은 길어짐. 측정값 해석 시 실패 횟수 같이 봐야 함.

측정 원칙:
- **JDBC 워밍업**: 측정 전 5,000 번 미리 실행 (커넥션 풀 / JIT 둘 다 워밍업)
- **5 회 평균**: 1 회만 보면 GC / autovacuum 영향 큼
- **매 측정 전 초기화**: `TRUNCATE` + `INSERT` 로 시작 상태 복원
- ⚠️ **측정 코드 중간에 `println` / log 절대 X** — 출력 한 줄에 동기화 효과가 섞임. 결과는 측정 끝난 후 한 번만 출력
- ⚠️ **`measurementLog.save()` 는 측정 완전히 끝난 후에 호출** (작업 중간 X)

> 표 양식 / 도구 / 단일 vs 멀티 비교 가이드 → [`CONTRIBUTING.md`](../../CONTRIBUTING.md#6-측정-가이드-s3-단계)

##### 3-2. 격리 수준 비교 + 해석

| 격리 수준 | Lost Update 방어 | TPS | 롤백 빈도 | 본인 도메인에 적합? |
|---|---|---|---|---|
| READ COMMITTED (PG 기본) | ❌ | (측정) | 0 | ? |
| REPEATABLE READ | ❌ (에러로 거부) | (측정) | (측정) | ? |
| SERIALIZABLE | ✅ | (측정) | (측정) | ? |

**해석 필수** (직답 받지 말고 본인 말로):
- "왜 SERIALIZABLE 에서 Lost Update 가 막히는가"
- "SERIALIZABLE TPS 가 낮은 이유 — 무엇이 비싼가"
- "REPEATABLE READ 에서 발생한 `serialization_failure` 를 실제 서비스라면 어떻게 처리할 것인가" (재시도? 사용자에게 에러? 큐로?)

> 힌트: `SQLState 40001` 이 오면 애플리케이션 레이어에서 잡아 재시도하는 게 표준 패턴. 몇 번까지 재시도할지, 사이 지연을 어떻게 줄지 (`exponential backoff` 키워드 검색) 본인 도메인에 맞춰 결정.

##### 3-3. 2 주차 결론 → 3 주차 브릿지

> "SERIALIZABLE 로 올리면 Lost Update 는 막히지만 TPS 가 X 까지 떨어진다.
> 더 나은 방법 — **필요한 row 에만 락을 거는 것** — 이 3 주차 주제."

이 한 줄을 본인 도메인 측정값으로 채워서 PR 본문에 적기.


#### ▸ STAGE 4 — 데드락 직접 만들기 (선택)

**목표**: PostgreSQL 이 데드락을 어떻게 감지하고 어느 쪽을 죽이는지 직접 본다.

##### 사전 조건 — row 2 개 이상 필요

데드락은 두 트랜잭션이 **서로 다른 row 를 다른 순서로** 잡을 때 발생한다. 단일 row 도메인은 데드락 못 만든다 (같은 row 는 순서 대기만 함). 본인 도메인이 단일 row 구조면 임시로 두 row 를 만들어 실험에만 사용 (예시는 `account` — 본인 도메인 테이블로 치환):

```sql
-- 예: 계좌 이체 도메인이면 자연스럽게 두 계좌 사용
INSERT INTO account (id, balance) VALUES (1, 10000), (2, 10000);
```

##### 데드락 시퀀스

세션 2 개로 손으로:
1. 세션 A: `BEGIN; UPDATE account SET balance = balance - 1000 WHERE id = 1;`
2. 세션 B: `BEGIN; UPDATE account SET balance = balance - 1000 WHERE id = 2;`
3. 세션 A: `UPDATE account SET balance = balance + 1000 WHERE id = 2;` (B 가 잡고 있음 → 대기)
4. 세션 B: `UPDATE account SET balance = balance + 1000 WHERE id = 1;` (A 가 잡고 있음 → 데드락)

PG 가 던지는 메시지:
```
ERROR:  deadlock detected
DETAIL: Process X waits for ShareLock on transaction Y; ...
```

##### 측정 항목

| 항목 | 의미 |
|---|---|
| **데드락 횟수** | PG 가 감지해서 abort 한 트랜잭션 수 (SQLState 40P01) |
| **성공 횟수** | 데드락 안 만나고 정상 commit 된 트랜잭션 수 |
| **기타 에러** | 40P01 이외의 SQLException |
| **응답시간** | 전체 라운드 끝나는 데 걸린 시간 |

`MeasurementLog.save("s4", "데드락 재현 (성공 X)", deadlocks, otherErrors, millis)` 형식으로 자동 누적.

##### 관찰 포인트

- PG 는 데드락을 **얼마 만에** 감지하는가? (`deadlock_timeout` 기본값 1초)
- 어느 쪽 트랜잭션이 죽는가? — 정책 (`pg_stat_activity` 로 확인)
- Java 코드에서 데드락 발생 시 어떤 SQLState 가 오는지 직접 확인 후 적기 (`SQLException.getSQLState()`)
- 같은 도메인을 N 스레드로 돌리면 데드락이 자연스럽게 일어나는 시나리오는?

##### 해석 — 응답시간 / 데드락 횟수 매칭

`데드락 횟수 × deadlock_timeout (1초)` 이 응답시간의 큰 부분을 차지하는지 본인 측정값으로 확인:

```
예: 100 라운드 시도, 데드락 50 회 → 50 초 + 본인 작업 시간
   응답시간이 50 초 근처면 PG 가 매 데드락마다 1 초 대기했다는 뜻.
   실서비스에서 데드락 자주 나면 응답 폭증 = 사용자 체감 장애.
```


━━━━━━━━━━━━━━━━━━━━━━━━━━
금지 키워드 — STAGE 1~2 동안만 (검색 금지)
━━━━━━━━━━━━━━━━━━━━━━━━━━
- `SELECT FOR UPDATE`, `SELECT FOR SHARE`, `LOCK TABLE` (3 주차 보호)
- 비관적 락 / 낙관적 락 / `@Version` (3 주차 보호)
- `@Transactional` 어노테이션 / Spring `TransactionTemplate` (4 주차 보호 + 이번 주 JDBC 직결 방향)

**락 관련은 3 주차까지 계속 금지** (`SELECT FOR UPDATE` 등). 격리 수준 변경 (`SET TRANSACTION ISOLATION LEVEL`) 은 STAGE 1 부터 사용 — 비교가 STAGE 1 핵심.


━━━━━━━━━━━━━━━━━━━━━━━━━━
학습 기록 (자유)
━━━━━━━━━━━━━━━━━━━━━━━━━━

### 2주차 참고 질문 (답하고 싶은 만큼만)
- 트랜잭션 격리 수준 4 가지와 각 단계에서 막히는 이상 현상
- PostgreSQL 의 `READ UNCOMMITTED` 가 `READ COMMITTED` 로 매핑되는 이유
- PostgreSQL `REPEATABLE READ` 와 MySQL `REPEATABLE READ` 의 동작 차이
- 본인 도메인에서 Lost Update 가 일어나는 정확한 시나리오 1 분 설명
- "트랜잭션을 붙였는데 왜 race 가 안 풀리는가"
- 1 주차의 `synchronized` / `AtomicLong` 와 2 주차의 격리 수준이 어떻게 같고 어떻게 다른가
- 본인 도메인이 정확성 우선 (포인트/결제) 인지 처리량 우선 (로그/통계) 인지에 따라 격리 수준을 어떻게 고를지 — 본인 측정값으로 근거 제시


━━━━━━━━━━━━━━━━━━━━━━━━━━
막힐 때
━━━━━━━━━━━━━━━━━━━━━━━━━━
1. 직접 시도 (`CLAUDE.md` 룰)
2. AI 에 물어보기 — 3 단계 힌트만 받음 (코드 직답 X)
3. 디스코드 `#질문` 채널 — 코드 + 에러 + 본인 도메인 schema.sql 함께
4. 그래도 안 되면 운영자 @멘션 (모임 1 일 전부터)

특히 **STAGE 1 에서 이상 현상이 재현 안 되는 경우**: 격리 수준을 SQL 로 직접 바꿨는지, 두 세션이 정말 분리돼 있는지 (같은 커넥션 X), 트랜잭션을 commit 했는지 먼저 확인.
