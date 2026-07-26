# 측정 기록

도메인: **계좌 이체** (`account` 테이블, id=1, id=2, balance=10000)

---

## STAGE 1 — 격리 수준별 이상 현상 (직접 관찰)

Lost Update 는 두 패턴 분리:
- **LU-atomic**: `UPDATE ... SET balance = balance - 1000` (한 문장)
- **LU-RMW**: `SELECT` → 앱 계산 → `UPDATE ... SET balance = ?` (절대값 세팅)

| 격리 수준        | Dirty Read | Non-repeatable | Phantom | LU-atomic | LU-RMW                  |
|--------------|---|---|---|---|-------------------------|
| READ UNCOMMITTED | 안 보임 (B 가 10000 읽음) | 발생 (= RC) | 발생 (= RC) | 안 보임 (= RC) | 발생 (= RC) |
| READ COMMITTED   | 안 보임 (RU=RC) | 발생 (10000 → 5000) | 발생 (count 2 → 3) | 안 보임 (8000, B 가 A 커밋 전까지 대기 → 재읽기) | **발생 (9000, 기댓값 8000)** |
| REPEATABLE READ  | 안 보임 (RU=RC) | 안 발생 (snapshot 유지) | 안 발생 (PG 의 RR 은 phantom 도 막음) | **막힘 (40001, snapshot 정상 잡혔을 때)** | **막힘 (40001)** |
| SERIALIZABLE     | 안 보임 | 안 발생 | 안 발생 | 막힘 (40001) | 막힘 (40001) |

### 관찰 노트

- **RU**: PG 는 READ UNCOMMITTED 를 READ COMMITTED 로 매핑 → Dirty Read 발생 안 함.
  출처: https://www.postgresql.org/docs/current/transaction-iso.html

- **LU-atomic 이 안 깨지는 이유 (row-level lock)**
  - PG 의 `UPDATE` 는 실행 순간 해당 row 에 row-level exclusive lock (`FOR NO KEY UPDATE`) 을 잡고 트랜잭션 종료 시점 (COMMIT/ROLLBACK) 까지 유지
  - A 의 UPDATE 가 lock 잡고 있으면 B 의 UPDATE 는 lock 풀릴 때까지 대기 → A 커밋 후 B 가 **다시 읽어** 새 값 (9000) 기준으로 계산 → 8000
  - lock 은 같은 row 의 UPDATE/DELETE/`SELECT FOR UPDATE` 만 막음. 일반 SELECT 는 안 막음

- **LU-RMW 가 깨지는 이유 (PG MVCC)**
  - PG 는 MVCC (Multi-Version Concurrency Control). 일반 `SELECT` 는 row lock 을 잡지 않고 트랜잭션 시작 시점 스냅샷을 읽음 → reader 와 writer 가 서로 안 막음
  - A, B 가 동시에 SELECT 해도 둘 다 같은 옛 값 (10000) 을 봄. lock 은 UPDATE 시점에 가서야 잡히는데, 그땐 두 트랜잭션 다 옛 값을 앱 메모리에 들고 있음
  - 결과: 두 UPDATE 가 같은 절대값 (9000) 을 쓰려 하고 한쪽이 다른 쪽을 덮음 → A 의 출금 효과가 사라짐 (Lost Update)
  - 출처: https://www.postgresql.org/docs/current/mvcc-intro.html

- **1주차 `count++` ↔ 2주차 RMW 매핑** — 같은 read-modify-write 패턴이 레이어만 바뀌어 반복

  | 1주차 (CPU / 메모리)        | 2주차 (DB / RMW)             |
  |---|---|
  | `LOAD count`              | `SELECT balance`            |
  | `ADD 1` (레지스터 계산)      | 앱에서 `balance - 1000` 계산  |
  | `STORE count`             | `UPDATE balance = ?`        |

  망가지는 모양도 1:1 — 두 주체가 모두 LOAD/SELECT 한 후 한쪽의 STORE/UPDATE 가 다른 쪽을 덮음.

- **사다리 한 줄**: race condition 은 사라진 게 아니라 한 층 위 (DB row) 로 올라온 것. 1주차에선 `synchronized` / `Atomic` 으로 막았고, 2주차에선 격리 수준 / 락으로 막아야 함.

- **RR + LU-RMW 가 막히는 이유 (Snapshot Isolation + first-committer-wins)**
  - PG 의 REPEATABLE READ 는 정확히는 **Snapshot Isolation**: 트랜잭션 시작 시점에 스냅샷 잡고 그 이후로는 그 스냅샷만 읽음
  - B 의 스냅샷에서는 row 가 여전히 10000 (A 의 commit 못 봄). B 가 UPDATE 치는 순간 PG 가 "내 스냅샷 시점 이후로 이 row 가 다른 트랜잭션에서 commit 됐다" 를 감지
  - → 그대로 UPDATE 하면 Lost Update 되므로 PG 가 거부: `ERROR: could not serialize access due to concurrent update` (SQLState `40001` = `serialization_failure`)
  - B 의 트랜잭션은 abort + 롤백 (그동안 한 작업 다 날아감)
  - 출처: https://www.postgresql.org/docs/current/transaction-iso.html#XACT-REPEATABLE-READ

- **RC vs RR 의 책임 위치 차이**

  | | RC | RR (Snapshot Isolation) |
  |---|---|---|
  | B 의 UPDATE | 9000 으로 그냥 덮어씀 | 에러 40001 + 롤백 |
  | Lost Update | 발생 (DB 가 묵인) | 막힘 (DB 가 거부) |
  | 책임 | 앱이 모르고 지나감 | 앱이 40001 잡아서 재시도 결정 |

  → RR 은 "더 안전" 처럼 보이지만 공짜 아님. 앱이 40001 잡고 재시도 (보통 exponential backoff) 안 짜면 사용자에게 그대로 에러 노출. 실서비스에서는 "잠시 후 다시 시도해주세요" 응답 또는 자동 재시도 로직 필수.

- **RR snapshot 시작 시점 — BEGIN 아니고 "첫 데이터 접근 시점"** ⚠️
  - PG 의 RR snapshot 은 `BEGIN` 시점이 아니라 **트랜잭션 안의 첫 SELECT/UPDATE 가 실행되는 시점** 에 잡힘
  - 결과: 같은 시나리오라도 B 가 BEGIN 후 곧바로 UPDATE 했나, SELECT 먼저 했나에 따라 동작이 달라짐
    - B 가 BEGIN; SET RR; 만 친 상태 → A 가 commit → B 가 UPDATE → B 의 snapshot 이 A commit 후에 시작 → 충돌 없음, 8000 정상
    - B 가 BEGIN; SET RR; SELECT → A 가 commit → B 가 UPDATE → B 의 snapshot 이 A commit 전에 시작 → 충돌 감지, 40001
  - 즉 **RR + atomic UPDATE 도 snapshot 이 충돌 범위 안에 있으면 40001 로 거부.** RMW 와 동일.
  - 출처: https://www.postgresql.org/docs/current/transaction-iso.html#XACT-REPEATABLE-READ
  - 디버깅 함의: "RR 인데 왜 어떨 땐 40001 어떨 땐 정상?" 의 답은 거의 항상 snapshot 시작 시점 때문

- **SR vs RR — 단일 row 시나리오에선 차이 없음**
  - SERIALIZABLE 은 RR + Serializable Snapshot Isolation (SSI). 본 도메인 (단일 계좌 row 출금) 에선 5 칸 결과가 RR 과 동일
  - SR 의 추가 가치는 **여러 row 를 읽고 쓸 때 발생하는 write skew** 같은 미묘한 케이스에서 나옴 (단일 row 출금에선 RR 이 이미 모두 막아줌)
  - 실서비스 트레이드오프: SR 은 더 빡빡하게 검사 → 더 자주 40001 → TPS 손해. STAGE 3 측정에서 정량 확인 예정

- **STAGE 1 결론**
  - PG 에서 Lost Update 를 막는 데 필요한 최소 격리 수준은 **REPEATABLE READ** (단, RMW 패턴은 snapshot 이 충돌 범위 안에 잡혀야 40001 발동)
  - RC 에서는 RMW 패턴이 묵인되므로 **계좌 / 결제 / 재고 같은 정확성 우선 도메인** 은 RC 만으로 부족
  - 막는 방법은 두 갈래:
    1. 격리 수준 RR/SR + 40001 재시도 로직 (다음 STAGE 2~3 에서 측정)
    2. 명시적 lock (`SELECT ... FOR UPDATE`) — 3 주차 주제

---

## STAGE 2 / 3 — 자동 누적 (코드 측정 시작 후)

```
- [MM-DD HH:MM] s2-1 · isolation=RC, 누락 X / 실패 Y / Zms
- [MM-DD HH:MM] s2-2 · 헬퍼 추출 후 isolation=RC, 누락 X / 실패 Y / Zms
- [MM-DD HH:MM] s3 · isolation=RR thr=200, 누락 0 / 실패 X / Yms
```
