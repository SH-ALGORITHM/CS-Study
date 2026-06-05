# 3주차 예시 코드 — 계좌 이체 도메인 (락 3 종 비교)

scenario.md 의 10 개 도메인과 **별개로** 만든 참고 코드입니다.
2 주차의 `BankAccount` 가 격리 수준 비교였다면, **이번엔 같은 도메인을 비관/낙관/분산락으로 푸는 버전**.

> ⚠️ **베끼지 마시고 본인 도메인으로 변환하세요.** "이런 식으로 흘러가는구나" 감을 잡는 용도.

## 2 주차와 무엇이 같고 다른가

| | 2 주차 BankAccount | 3 주차 BankAccount |
|---|---|---|
| race 차단 방법 | 격리 수준 ↑ (RC → SR) | 락 ↓ (FOR UPDATE / version / Redis SETNX) |
| race 잡는 단위 | 트랜잭션 전체 (스냅샷) | 필요한 row / 자원만 |
| 학습 포인트 | 격리 수준의 비용 / 한계 | 락 도구별 trade-off + 데드락 |
| 새로 다루는 도구 | — | `SELECT FOR UPDATE` / `version` 컬럼 / Redis SETNX |

코드 구조는 read-modify-write 패턴 그대로. 2주차에서 SERIALIZABLE 로 비싸게 막던 것을 락으로 정밀하게 막는 흐름.

## 폴더 구조

```
example/
├── README.md                              # 지금 이 파일
├── build.gradle                           # HikariCP + PostgreSQL JDBC + Lettuce (Redis)
└── src/main/
    ├── java/
    │   ├── stage/                         # 학습자가 ▶ 누르는 main
    │   │   ├── Stage2Pessimistic.java     # s2-1: SELECT FOR UPDATE
    │   │   ├── Stage2Optimistic.java      # s2-2: version 컬럼
    │   │   ├── Stage2Distributed.java     # s2-3: Redis SETNX + Lua
    │   │   ├── Stage3Compare.java         # s3:   충돌 빈도별 3 락 비교
    │   │   └── Stage4Deadlock.java        # s4:   데드락 재현 + 4 조건
    │   ├── domain/
    │   │   └── BankAccount.java           # 도메인 — RMW 형태로 짠 race 발생 코드
    │   └── infra/                         # DB / Redis 연결 / 스키마 / 측정 로그
    │       ├── DataSourceFactory.java     # HikariCP 셋업
    │       ├── RedisClientFactory.java    # Lettuce 셋업
    │       ├── SchemaBootstrap.java       # account 테이블 + version 컬럼 + 초기 INSERT
    │       └── MeasurementLog.java        # 1, 2 주차와 동일 (자동 누적)
    └── resources/
        ├── schema.sql                     # 수동 셋업 시 참고용 (Java 가 자동 생성)
        └── Stage1Manual.sql               # STAGE 1 손 측정용 SQL 시퀀스 (FOR UPDATE / 데드락 / Redis)
```

## 실행 전 — DB + Redis 띄우기

```bash
# 프로젝트 루트에서
docker compose up -d                              # postgres + redis 기동
docker exec csstudy-postgres psql -U csstudy -d csstudy -c "SELECT 1"   # → 1
docker exec csstudy-redis redis-cli PING                                  # → PONG
```

## STAGE 별 실행

| 파일 | 단계 | 보는 것 |
|---|---|---|
| `stage/Stage2Pessimistic` | s2-1 | `SELECT FOR UPDATE` 로 row 잠금 — 데드락 회피 (id 작은 것부터) |
| `stage/Stage2Optimistic` | s2-2 | `version` 컬럼 + `UPDATE WHERE version = ?` — 충돌 시 재시도 |
| `stage/Stage2Distributed` | s2-3 | Redis `SET NX EX` 잠금 + Lua script 안전 해제 |
| `stage/Stage3Compare` | s3 | 충돌 빈도 낮/중/높 3 단계 × 3 락 = 9 케이스 측정 |
| `stage/Stage4Deadlock` | s4 | 일부러 락 잡는 순서 깨기 → 데드락 발생 → 4 조건 매핑 |

각 파일 main 옆 ▶ 클릭. 콘솔 + `measurements.md` 자동 누적.

## 본인 도메인으로 변환할 때

| 계좌 이체 | → | 본인 도메인 (예: 주식 매수/매도) |
|---|---|---|
| `BankAccount` | → | `StockTrade` |
| `account.balance` (NUMERIC) | → | `holding.quantity` + `wallet.balance` |
| `transfer(from, to, amount)` | → | `buy(userId, ticker, qty, price)` |
| 두 계좌 동시 UPDATE | → | 현금 잔고 + 보유 주식 동시 UPDATE |

**구조는 같음, 컬럼/테이블 이름만 달라짐.**

## STAGE 1 (psql / DBeaver 손으로) 안내

STAGE 1 은 Java 코드가 아니라 psql / DBeaver 두 세션으로 손으로 보는 단계.
시나리오 `topics/03-lock/scenario.md` 의 **STAGE 1** 섹션을 따라가면 됨.

편의를 위해 **`src/main/resources/Stage1Manual.sql`** 에 모든 SQL 시퀀스를 미리 정리. DBeaver 에서 그 파일 열고 블록 단위로 실행. (참고용 — 본인 도메인이 다르면 컬럼/테이블 이름 치환 필요)

## 주의사항

- 본인 폴더 (`members/{본인이름}/`) 에 본인 도메인 코드 작성 — 여기 example 은 참고용
- 측정 코드 중간에 `println` / log 절대 X — 출력 한 줄에 동기화 효과
- `MeasurementLog.save()` 는 측정 끝난 후에 한 번만 호출
- `executor.awaitTermination()` 충분히 큰 값 (300 초 이상) — 2 주차 timeout 컷 사건 교훈
- AI 에게 "이 코드 짜줘" 금지 — 본인이 시도 후 힌트 받기 (`CLAUDE.md` 룰)
- HikariCP autoCommit 기본값은 `true` — `setAutoCommit(false)` 안 부르면 트랜잭션 안 쓴 것과 동일
- **Redis 분산락 해제는 반드시 Lua script** — 단순 `DEL` 하면 TTL 만료 후 다른 lock 풀어버리는 사고 발생
