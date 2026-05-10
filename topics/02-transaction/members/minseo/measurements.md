## STAGE 1 — 격리 수준별 이상 현상 (직접 관찰)

회의실 예약 도메인 (`meeting_room_booking`) 을 세션 2개로 나누어 직접 관찰한 결과입니다.

| 격리 수준 | Dirty Read | Non-repeatable | Phantom | Lost Update (RMW) |
|---|:---:|:---:|:---:|:---:|
| READ UNCOMMITTED | ❌ (방어) | ⭕ (발생) | ⭕ (발생) | ⭕ (발생) |
| READ COMMITTED | ❌ (방어) | ⭕ (발생) | ⭕ (발생) | ⭕ (발생) |
| REPEATABLE READ | ❌ (방어) | ❌ (방어) | ❌ (방어*) | ❌ (에러 방어) |
| SERIALIZABLE | ❌ (방어) | ❌ (방어) | ❌ (방어) | ❌ (에러 방어) |

*참고: PostgreSQL의 REPEATABLE READ는 Snapshot Isolation을 통해 Phantom Read를 방어함.

### 관찰 노트

#### 1. Dirty Read (READ UNCOMMITTED)
- **관찰 결과**: `SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED`를 설정하고 세션 B에서 데이터를 수정한 뒤(커밋 전) 세션 A에서 조회했으나, 수정된 값이 아닌 기존 데이터(`Admin`)가 조회됨.
- **나의 해석**: PostgreSQL 공식 문서에 따르면 `READ UNCOMMITTED`는 내부적으로 `READ COMMITTED`와 동일하게 동작함. MVCC 구조상 커밋되지 않은 데이터는 다른 트랜잭션에서 읽을 수 없도록 설계되어 있어 Dirty Read가 발생하지 않음을 확인함.

#### 2. Non-repeatable Read (READ COMMITTED)
- **관찰 결과**: 세션 A가 데이터를 조회한 후, 세션 B가 해당 데이터를 수정하고 커밋함. 이후 세션 A가 다시 조회했을 때 값이 `Admin`에서 `Hacker`로 변경된 것을 확인.
- **나의 해석**: `READ COMMITTED`는 이름 그대로 '커밋된 데이터는 읽는다'는 원칙을 지킴. 하지만 이로 인해 한 트랜잭션 내에서 데이터의 일관성이 깨지는 현상이 발생함. "상식적으로 커밋된 게 보이는 게 맞지 않나?"라는 의문이 들었으나, 정교한 로직에서는 트랜잭션 내 일관성이 더 중요할 수 있음을 깨달음.

#### 3. Phantom Read (READ COMMITTED)
- **관찰 결과**: 세션 A가 전체 건수를 조회(`COUNT(*)`)했을 때 1건이었으나, 세션 B가 새로운 행을 `INSERT`하고 커밋한 뒤 다시 조회하자 2건으로 늘어난 유령(Phantom) 데이터를 확인.
- **나의 해석**: 기존 행의 수정뿐만 아니라 새로운 행의 삽입 역시 `READ COMMITTED` 수준에서는 막지 못함. 회의실 예약 시스템에서 "빈 방 확인 후 예약" 로직을 짤 때 이 현상 때문에 중복 예약이 발생할 수 있겠다는 위험성을 인지함.

#### 4. Lost Update (RMW 패턴 vs Atomic UPDATE)
- **RMW 관찰 결과**: 두 세션이 동시에 `SELECT` 후 절대값으로 `UPDATE`를 쳤을 때, 먼저 커밋한 A의 작업이 무시되고 나중에 커밋한 B의 작업만 남음 (`B_User`).
- **Atomic 관찰 결과**: `SET x = x || '_B'` 와 같이 한 문장으로 업데이트했을 때는 DB가 B를 잠시 대기(Wait)시켰다가, A가 커밋한 최신값 위에서 B가 작업을 이어가게 하여 `Original_A_B`라는 올바른 결과를 얻음.
- **나의 해석**: 1주차의 Race Condition이 DB에서도 그대로 재현됨을 확인. 특히 SQL을 어떻게 작성하느냐(RMW vs Atomic)에 따라 격리 수준이 같아도 결과가 완전히 달라질 수 있다는 점이 매우 인상적이었음.

#### 5. PostgreSQL REPEATABLE READ의 독특한 동작
- **관찰 결과**: `REPEATABLE READ` 수준에서 동시에 같은 행을 수정하려 했을 때, 나중에 온 세션 B가 대기하다가 세션 A가 커밋하는 순간 `ERROR: could not serialize access due to concurrent update (SQLState: 40001)` 에러를 뱉으며 종료됨.
- **나의 해석**: PostgreSQL은 "First-Updater-Wins" 전략을 통해 데이터 유실을 강제로 막음. 덮어쓰기를 허용하는 대신 에러를 던져서 정합성을 지키며, 이 경우 애플리케이션 레벨에서 재시도(Retry) 로직이 반드시 필요함을 이해함.
