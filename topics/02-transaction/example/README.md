# 2주차 예시 코드 — 은행 계좌 도메인 (DB 버전)

scenario.md 의 9 개 도메인과 **별개로** 만든 참고 코드입니다.
1주차의 `BankAccount` 가 메모리 race 였다면, **이번엔 같은 도메인을 DB 로 옮긴 버전**.

> ⚠️ **베끼지 마시고 본인 도메인으로 변환하세요.** 이건 "이런 식으로 흘러가는구나" 감을 잡는 용도.

## 1주차와 무엇이 같고 다른가

| | 1주차 BankAccount | 2주차 BankAccount |
|---|---|---|
| 잔고 위치 | JVM 메모리 (`int balance`) | DB 컬럼 (`account.balance`) |
| race 발생 지점 | `if (balance >= amount) { balance -= amount }` | `SELECT balance` → 앱 계산 → `UPDATE balance = ?` |
| 학습 포인트 | race condition 자체 | 트랜잭션 / 격리 수준이 race 를 어떻게 막거나 못 막는가 |

코드 구조는 read-modify-write 패턴 그대로. 1주차 `count++` 가 망가진 것과 정확히 같은 구조.

## 폴더 구조

```
example/
├── README.md                              # 지금 이 파일
├── build.gradle                           # HikariCP + PostgreSQL JDBC 의존성
└── src/main/
    ├── java/
    │   ├── stage/                         # 학습자가 ▶ 누르는 main 4 개
    │   │   ├── Stage2RaceJdbc.java        # s2-1: 헬퍼 없이 손으로
    │   │   ├── Stage2WithHelper.java      # s2-2: TransactionHelper 사용
    │   │   ├── Stage3Measurement.java     # s3:   격리 수준 4 단계 비교
    │   │   └── Stage4Deadlock.java        # s4:   데드락 재현 (선택)
    │   ├── domain/
    │   │   └── BankAccount.java           # 도메인 — RMW 형태로 의도적으로 짠 race 발생 코드
    │   └── infra/                         # DB 연결 / 스키마 / 헬퍼 / 측정 로그
    │       ├── DataSourceFactory.java     # HikariCP 셋업 (csstudy DB 접속)
    │       ├── SchemaBootstrap.java       # account 테이블 + 초기 INSERT (idempotent)
    │       ├── TransactionHelper.java     # STAGE 2-2 의 정답 참고 — 본인이 만든 헬퍼와 비교
    │       └── MeasurementLog.java        # 1주차와 동일 (자동 누적)
    └── resources/
        └── schema.sql                     # 수동 셋업 시 참고용 (Java 가 자동 생성하므로 필수 X)
```

## 실행 전 — DB 띄우기

```bash
# 프로젝트 루트에서
docker compose up -d                      # postgres + redis 기동
docker exec csstudy-postgres psql -U csstudy -d csstudy -c "SELECT 1"
# 결과에 1 나오면 정상
```

## STAGE 별 실행

| 파일 | 단계 | 보는 것 |
|---|---|---|
| `stage/Stage2RaceJdbc` | s2-1 | 트랜잭션을 손으로 (헬퍼 없음) — 같은 try-catch 반복을 직접 보기 |
| `stage/Stage2WithHelper` | s2-2 | 같은 로직을 `TransactionHelper` 로 추출 — 본인 헬퍼와 비교 |

> ⚠️ **STAGE 2-1 을 건너뛰고 헬퍼부터 쓰지 말 것.** 손으로 try-catch / setAutoCommit / commit / rollback 을 직접 다뤄본 다음에 헬퍼를 봐야 "이 헬퍼가 무엇을 자동화하는지" 가 보인다. 4 주차에서 `@Transactional` 을 만났을 때 "내가 만든 헬퍼의 강화판" 으로 인식하는 게 학습 자산이다.

| `stage/Stage3Measurement` | s3 | READ_COMMITTED / REPEATABLE_READ / SERIALIZABLE 격리 수준 비교 |
| `stage/Stage4Deadlock` | s4 (선택) | 두 계좌 반대 순서 UPDATE → PG 데드락 감지 직접 보기 |

각 파일 main 메서드 옆 ▶ 클릭. 콘솔에 결과 + `measurements.md` 자동 누적.

## 본인 도메인으로 변환할 때

| 은행 계좌 | → | 본인 도메인 (예: 콘서트 좌석 예약) |
|---|---|---|
| `BankAccount` | → | `SeatReservation` |
| `account.balance` (BIGINT) | → | `seat.reserved_by` (BIGINT NULL) |
| `withdraw(conn, amount)` | → | `reserve(conn, userId)` |
| 잔고 100, 1 원씩 출금 | → | 좌석 100 개, 1 명씩 예약 |

**구조는 같음, 컬럼/테이블 이름만 달라짐.**

## STAGE 1 (psql 손으로) 안내

STAGE 1 은 Java 코드가 아니라 psql / DBeaver 두 세션으로 손으로 보는 단계.
시나리오 `topics/02-transaction/scenario.md` 의 **STAGE 1** 섹션을 따라가면 됨.

편의를 위해 **`src/main/resources/Stage1Manual.sql`** 에 모든 SQL 시퀀스를 미리 정리해둠. DBeaver 에서 그 파일 열고 블록 단위로 실행하면 됨. (참고용 — 본인 도메인이 다르면 컬럼/테이블 이름 치환 필요)

## 주의사항

- 본인 폴더 (`members/{본인이름}/`) 에 본인 도메인 코드 작성 — 여기 example 은 참고용
- 측정 코드 중간에 `println` / log 절대 X — 출력 한 줄에 동기화 효과 (1주차 룰)
- `MeasurementLog.save()` 는 측정 끝난 후에 한 번만 호출
- AI 에게 "이 코드 짜줘" 금지 — 본인이 시도 후 힌트 받기 (`CLAUDE.md` 룰)
- HikariCP autoCommit 기본값은 `true` — `setAutoCommit(false)` 안 부르면 트랜잭션 안 쓴 것과 동일
