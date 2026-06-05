# 3주차 — 격리 수준 올리지 말고, 필요한 자원만 잠그자 (DB 락 + 분산락)

이번 주제: 2주차에서 격리 수준 ↑ 만으로는 race 를 못 막거나 (RR 의 INSERT race), 막더라도 비용이 컸다 (SR 의 abort + 재시도). **필요한 row / 자원만 명시적으로 잠그는 방법** 을 익힌다.

3 가지 락 비교 + 데드락:
- 비관적 락 (`SELECT FOR UPDATE`) — 미리 잠그고 작업, 대기 비용
- 낙관적 락 (`version` 컬럼 + `UPDATE WHERE version = ?`) — 잠그지 않고 작업, 충돌 시 재시도
- 분산락 (Redis `SETNX + TTL`) — DB 외부 / 여러 서버 인스턴스 자원 보호
- 데드락 — 4 조건 + 식사하는 철학자 + 직접 재현

---

## 우선 알아둬야 할 단어 (시작 전 1분)

| 단어 | 풀어쓰면 |
|---|---|
| **비관적 락 (Pessimistic)** | "충돌 일어날 것이라 가정하고 미리 잠근다" — `SELECT ... FOR UPDATE` 로 row lock |
| **낙관적 락 (Optimistic)** | "충돌 안 일어날 것이라 가정, 일어나면 재시도" — `version` 컬럼 비교로 충돌 감지 |
| **분산락 (Distributed Lock)** | DB 와 무관, 여러 서비스 인스턴스 / 외부 자원 보호. Redis `SETNX` 가 가장 흔함 |
| **Lock wait** | 다른 트랜잭션이 잡은 row 의 lock 이 풀릴 때까지 대기 |
| **Deadlock** | 두 트랜잭션이 서로의 lock 을 기다리는 상태. PG 는 1 초 후 감지 + 한쪽 abort |
| **데드락 4 조건** | 상호 배제 / 점유 대기 / 비선점 / 순환 대기 — 4 개 다 만족해야 데드락 |
| **식사하는 철학자** | 5 명이 포크 5 개를 공유하는 고전 데드락 시나리오 — 자원 잡는 순서가 문제 |
| **충돌 빈도 (contention)** | 동시에 같은 자원 노리는 비율. 낮으면 낙관이 유리, 높으면 비관이 유리 |


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 0. 개념 숙지 (도메인 무관, 모두 동일)
━━━━━━━━━━━━━━━━━━━━━━━━━━

## 필수 개념 (AI 와 대화하며 숙지)

### DB 락
1. `SELECT FOR UPDATE` 의 동작 — 잠그는 대상 / 잠금 해제 시점 / 다른 트랜잭션 영향
2. `SELECT FOR SHARE` 와 `FOR UPDATE` 차이 (S-lock vs X-lock)
3. 낙관적 락 구현 — `version` 컬럼 + `UPDATE WHERE version = ?` + 영향 row 수 검사
4. 비관 vs 낙관 trade-off — 충돌 빈도에 따라 어느 쪽이 유리한지

### 분산락
5. **DB 락만으로 못 푸는 케이스 1 개** — 여러 서비스 인스턴스 / 외부 API 호출 직렬화 / 스케줄러 중복 방지
6. Redis 분산락 메커니즘 — `SETNX key value` + `EXPIRE` (또는 `SET key value NX EX seconds`)
7. TTL 의 의미 — lock 보유자가 죽어도 자동 해제. 단 TTL 짧으면 작업 중 풀릴 위험
8. DB 락 vs Redis 락 차이 — 범위 / 해제 / 데드락 감지 / 장애 대응

### 데드락 (운영체제 고전 문제 — DB 락도 같은 메커니즘)
9. **데드락 4 조건 = Coffman 조건** (Coffman et al. 1971, OS 교과서 표준) — 상호 배제 (mutual exclusion) / 점유 대기 (hold and wait) / 비선점 (no preemption) / 순환 대기 (circular wait). 4 개 **다** 만족해야 데드락 발생. 1 개라도 깨면 회피
10. **식사하는 철학자 (Dijkstra 1965)** — 5 명이 5 개 포크 양쪽에서 잡으려 함. 모두 왼쪽 먼저 잡으면 데드락. 해결책 (자원 잡는 순서 통일 / 비선점 / hold-and-wait 깨기) 직접 정리
11. **OS 자원 할당 그래프** 와의 매핑 — PG 의 lock wait graph 는 OS 의 자원 할당 그래프와 같은 구조. **그래프에 사이클이 있으면 데드락**. PG 의 `pg_locks` 가 OS 의 자원 할당 테이블에 해당
12. PG `deadlock_timeout` 기본값 (1 초) + **deadlock victim 선택 기준** — 가장 적은 작업 (undo 비용 최소) 을 한 트랜잭션을 abort. 실제로는 나중에 진입한 쪽이 더 자주 죽는 경향
13. SQLState `40001` (serialization_failure, SR) vs `40P01` (deadlock_detected) 차이

## 자기 검증 (입으로 답할 수 있어야 STAGE 1 시작)

**★ 관문 — 이 3 개는 입으로 답해야 STAGE 1 진입**
- [ ] ★ 비관 vs 낙관 trade-off — 충돌 빈도가 어떻게 영향 주는가
- [ ] ★ **DB 락이 못 막고 Redis 분산락이 필요한 케이스** 1 개 본인 표현으로
- [ ] ★ 데드락 4 조건 (Coffman 조건) 을 본인 말로 1 분 설명

**보너스 — STAGE 1 진행하면서 답할 수 있게**
- [ ] `SELECT FOR UPDATE` 가 격리 수준 RC 에서도 효과 있는 이유
- [ ] 낙관적 락이 "충돌" 을 어떻게 알아채는가 (UPDATE 의 row 수)
- [ ] 식사하는 철학자에서 데드락을 깨는 방법 1 개
- [ ] PG 가 데드락을 감지하면 어떤 SQLState 가 어플리케이션으로 오는가
- [ ] 2 주차 SSI abort 와 3 주차 낙관적 락 재시도가 같은 점 / 다른 점


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 1. 본인 도메인 선택 — 3주차에 맞게 (UPDATE 패턴 + 데드락 가능 구조)
━━━━━━━━━━━━━━━━━━━━━━━━━━

3주차 학습 포인트 (**row 단위 lock + 데드락 + 비관/낙관/분산 비교**) 는 **UPDATE 가 많고 row 2 개 이상을 동시에 다루는 도메인** 에서 잘 드러난다. 2주차에 INSERT 위주 도메인 (호텔, 회의실) 을 했던 사람은 새 도메인 선택.

## 후보 도메인 + 적합도 (10 개 — 7 명이 1 개씩 + 여유 3)

| # | 도메인 | UPDATE 패턴 | 데드락 | 분산락 자연스러움 | 메모 |
|---|---|---|---|---|---|
| 1 | **계좌 이체** (`account`) | ★★★ | ★★★ | ★★★ | 두 row (from / to) UPDATE. 면접 단골 "동시 매수 잔고 음수 방지" 직결 |
| 2 | **주식 매수/매도** (`stock_trade`) | ★★★ | ★★★ | ★★★ | 현금 잔고 + 보유 주식 row 동시. 외부 거래소 호출 = 분산락 자연스러움 |
| 3 | **재고 입출고 (다중 창고)** (`inventory`) | ★★★ | ★★ | ★★ | 창고 / 상품 매트릭스. 창고 간 이동 시 데드락 |
| 4 | **환전 / 통화 거래** (`wallet`) | ★★★ | ★★★ | ★★ | 두 통화 row UPDATE. 2주차 한재훈과 겹침 |
| 5 | **장바구니 결제** (`checkout`) | ★★ | ★★★ | ★★ | 재고 + 포인트 두 테이블. 2주차 가빈과 겹침 |
| 6 | **티켓 예매** (`ticket_booking`) | ★★ | ★★★ | ★★★ | 좌석 + 잔고. 외부 결제 시스템 호출 |
| 7 | **선착순 쿠폰** (`coupon_event`) | ★★★ | ★ | ★★★ | 단일 row → 데드락 약함. 충돌 빈도는 극단적 |
| 8 | **콘서트 좌석 예약** (`seat`) | ★★ | ★ | ★★ | 단일 좌석 → 데드락 약함. 2주차 수진과 겹침 |
| 9 | **포인트 적립/차감** (`user_point`) | ★★ | ★ | ★★ | 가장 단순 — 입문자 추천 |
| 10 | **경매 / 입찰** (`auction_bid`) | ★★★ | ★ | ★★★ | 최고 입찰가 RMW + 동시 입찰 Lost Update. 단일 row 데드락 약함 |
| 11 | **P2P 송금** (`user_wallet`) | ★★★ | ★★★ | ★★★ | 계좌 이체 + 수수료 + 일일 한도 검증. row 3 개 (송금자/수취자/수수료 수익) 데드락 더 풍부 |
| 12 | **게임 머니 거래** (`game_inventory`) | ★★★ | ★★★ | ★★ | 캐릭터 A → B 골드 + 아이템 동시 이동. 트레이드 시스템 |
| 13 | **환불 처리** (`payment`, `refund`) | ★★★ | ★★ | ★★★ | 결제 row → 환불 row 역방향. 외부 결제 게이트웨이 호출 시 분산락 |

> **데드락 ★★★ 조건** = row 2 개 이상을 다른 트랜잭션이 다른 순서로 잡을 수 있는 구조. 단일 row 도메인 (8, 9) 은 데드락 못 만듦 → STAGE 4 학습이 약함.

## 도메인별 예상 시나리오 한 줄

| # | 한 줄 시나리오 |
|---|---|
| 1 | A 가 1→2 이체, B 가 2→1 이체 → 락 잡는 순서 다르면 데드락. 같은 계좌 동시 출금 시 잔고 음수 위험 |
| 2 | A 가 삼성전자 매수 (현금↓ 보유주식↑), B 가 동일 종목 매도 (현금↑ 보유주식↓) → 두 row 다른 순서 잡으면 데드락. 동시 매수 시 잔고 음수 |
| 3 | 창고 A → 창고 B 이동 (출고 + 입고). 두 이동이 반대 순서로 row 잡으면 데드락 |
| 4 | A 가 KRW→USD, B 가 USD→KRW → 두 통화 row 다른 순서 잡으면 데드락 |
| 5 | 결제 1 은 재고→포인트 순서, 결제 2 는 포인트→재고 순서 → 데드락. 두 사용자가 마지막 1 개 동시 결제 시 한쪽 음수 |
| 6 | 티켓 예매 (좌석 잠금 + 결제 외부 API + 잔고 차감) — 외부 API 직렬화에 분산락 필수 |
| 7 | 100 개 쿠폰을 1000 명이 동시 시도 → 단일 row 에 극단적 충돌. 비관 vs 낙관 응답시간 역전 가능 |
| 8 | A 와 B 가 같은 좌석 동시 예약 → 단일 row Lost Update. 인기 좌석에 충돌 집중 |
| 9 | A 가 적립, B 가 차감을 동시 → 단일 row Lost Update. 가장 단순한 RMW |
| 10 | 현재 최고가 10,000 원. A 가 11,000, B 가 12,000 동시 입찰 → 둘 다 10,000 보고 본인 가격으로 UPDATE → 한쪽 사라짐. 비관/낙관 비교에 좋음 |
| 11 | 사용자 A → 사용자 B 1,000 원 송금 (수수료 10 원). A 의 잔액 차감 + B 의 잔액 증가 + 수수료 row 증가 — row 3 개로 데드락 학습 가장 풍부. 일일 송금 한도까지 추가하면 RMW 검증 단계 ↑ |
| 12 | 캐릭터 A 가 캐릭터 B 에게 골드 1000 + 아이템 1 개 거래. 두 캐릭터 row + 아이템 row 동시 UPDATE. 트레이드 시스템 |
| 13 | 결제 ID 123 (1 만원) 환불 처리. payment.status='REFUNDED' UPDATE + refund row INSERT + 사용자 잔액 복원. 외부 결제 게이트웨이 환불 API 호출이 분산락 자연스러움 |

## 학습자 프로필별 추천

| 본인 상황 | 추천 도메인 |
|---|---|
| 2주차에 INSERT 도메인 (호텔, 회의실) 했음 | **1 계좌 이체** 또는 **2 주식 매수/매도** — UPDATE 위주로 결 바꿔 학습 폭 ↑ |
| 면접 답변 준비 (동시 매수 잔고 음수 / 데드락) | **1** 또는 **2** — 면접 단골 직결 |
| 분산락 깊이 학습하고 싶음 | **2 주식** 또는 **6 티켓 예매** — 외부 API 호출 자연스러움 |
| 비관 vs 낙관 응답시간 역전 보고 싶음 | **7 선착순 쿠폰** — 단일 row 극단적 충돌. 데드락 학습은 약함 |
| 입문자 / 작년 1 주차 멤버 | **9 포인트** — RMW 단순 구조부터 익히기 |
| 2주차 도메인 그대로 쓰고 싶음 (선택) | 2주차 호텔/회의실 (INSERT) 한 멤버는 비추천 — 락 학습에 약함 |

## 2 주차 도메인이 약한 이유

2 주차에 호텔 / 회의실 (INSERT 위주) 했던 사람이 그대로 가면:
- `SELECT FOR UPDATE` 는 **존재하는 row 만 잠금** — 아직 INSERT 안 된 시간대는 잠글 게 없음
- 락보다 EXCLUDE constraint 가 자연 해법 (이미 2 주차에 함)
- 데드락 학습이 약함 (보통 INSERT 1 회 단위라 row 여러 개 동시 잡기 부자연)

→ 호텔 / 회의실 출신은 **1 계좌 이체 / 2 주식 / 3 재고 / 7 쿠폰** 중 선택 추천.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 2. DB 테이블 매핑
━━━━━━━━━━━━━━━━━━━━━━━━━━

| 도메인 | 테이블 | 핵심 컬럼 | 락 학습 SQL (read-modify-write + version) |
|---|---|---|---|
| 1 계좌 이체 | `account` | `id, balance NUMERIC, version BIGINT` | 비관: `SELECT balance FROM account WHERE id = ? FOR UPDATE` (두 계좌 id 작은 것부터) → `UPDATE`. 낙관: `UPDATE account SET balance = ?, version = version + 1 WHERE id = ? AND version = ?` |
| 2 주식 매수/매도 | `wallet`, `holding` | wallet: `user_id, cash`. holding: `user_id, ticker, qty, version` | 비관: 두 테이블 row `FOR UPDATE` (순서 고정 → wallet 먼저 / holding 나중). 분산: 종목별 Redis lock (`lock:ticker:삼성전자`) → 외부 거래소 API 직렬화 |
| 3 재고 입출고 | `inventory` | `warehouse_id, item_id, quantity, version` (PK: 두 컬럼) | 비관: 두 창고 row `FOR UPDATE` (창고 id 작은 것부터). 분산: 상품별 lock (`lock:item:item-1`) |
| 4 환전 | `wallet` | `user_id, currency, balance, version` | 비관: 두 통화 row `FOR UPDATE` (currency 알파벳 순). 데드락 재현은 KRW↔USD 다른 순서로 |
| 5 장바구니 결제 | `stock`, `user_point` | 두 테이블 — `quantity` / `balance` 둘 다 `version` | 비관: 항상 stock 먼저 → user_point. 일부러 반대로 짜면 STAGE 4 데드락 시연 |
| 6 티켓 예매 | `seat`, `user_wallet`, `ticket_payment` | seat: `concert_id, seat_no, reserved_by`. wallet: `balance, version` | 비관: seat `FOR UPDATE` + 외부 결제 API 호출. **분산락 필수** — 결제 API 중복 호출 방지 (`lock:payment:사용자id`) |
| 7 선착순 쿠폰 | `coupon_event` | `event_id, remaining_count, version` | 비관: `FOR UPDATE` → `UPDATE remaining_count = ? - 1`. 낙관: version. 분산: `lock:event:event-1` 로 여러 인스턴스 직렬화 |
| 8 콘서트 좌석 | `seat` | `concert_id, seat_no, reserved_by BIGINT NULL, version` | 비관: 단일 row `FOR UPDATE` → `UPDATE reserved_by`. 낙관: `UPDATE WHERE reserved_by IS NULL AND version = ?` |
| 9 포인트 적립/차감 | `user_point` | `user_id, balance, version` | 단순 RMW — 비관/낙관 코드 가장 짧음. 데드락 학습 약함 |
| 10 경매 / 입찰 | `auction_bid` | `auction_id, current_price, last_bidder_id, version` | 비관: `FOR UPDATE` → 본인 가격이 더 높으면 `UPDATE`. 낙관: version 비교. 분산: 인기 경매 lock (`lock:auction:1`) |
| 11 P2P 송금 | `user_wallet`, `fee_account` | wallet: `user_id, balance, daily_sent, version`. fee_account: `id=0, balance` (시스템 수수료) | 비관: 3 row `FOR UPDATE` (송금자/수취자/수수료, id 순서) — 데드락 학습 풍부. 낙관: 3 row version. 분산: 송금자 단위 lock (`lock:user:sender_id`) — 일일 한도 race 방지 |
| 12 게임 머니 거래 | `character`, `inventory` | character: `id, gold, version`. inventory: `character_id, item_id, qty, version` | 비관: character 2 row + inventory row `FOR UPDATE` (캐릭터 id 작은 것부터). 분산: 트레이드 세션별 lock |
| 13 환불 처리 | `payment`, `refund`, `user_wallet` | payment: `id, status, amount`. refund: `payment_id, amount, status`. wallet: `balance, version` | 비관: payment 행 `FOR UPDATE` → refund INSERT → wallet UPDATE. 분산: **외부 PG API 환불 호출 직렬화 필수** (`lock:refund:payment_id`) |

각자 본인 도메인의 `schema.sql` 작성 — 초기 row + version 컬럼 + 시작값 세팅:

```sql
-- 예: 계좌 이체 도메인
CREATE TABLE IF NOT EXISTS account (
    id BIGINT PRIMARY KEY,
    balance NUMERIC NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
INSERT INTO account (id, balance) VALUES (1, 10000), (2, 10000)
ON CONFLICT (id) DO UPDATE SET balance = EXCLUDED.balance, version = 0;
```

> 측정 시작 전 매번 `TRUNCATE` + `INSERT` 로 초기 상태 복원.

> ⚠️ **반드시 read-modify-write 형태로 짤 것.** `UPDATE ... SET balance = balance - ?` 같은 atomic UPDATE 로 짜면 PG row-lock 이 자동으로 막아 락 학습 포인트가 안 보인다. 2 주차와 같은 원칙.

## Redis 분산락 — SETNX + TTL + Lua script (분산락 학습자 모두)

PostgreSQL 트랜잭션 락과 별개로 Redis 로 분산락. Lettuce 클라이언트 사용 (`build.gradle` 에 `io.lettuce:lettuce-core:6.3.0.RELEASE`). 상세 코드 + 싱글턴 패턴은 STAGE 2-3 참조.

**4 가지 주의점**:
1. **`SET key value NX EX seconds`** 한 번에 — 잠금 + TTL 원자 설정. `SETNX` + `EXPIRE` 따로 하면 사이에 죽을 위험
2. **해제는 Lua script** — 단순 `DEL` 하면 TTL 만료 후 다른 트랜잭션 lock 풀어버림. `get + del` 원자화 필요
3. **`RedisClient` 는 싱글턴** — 매 요청마다 생성하면 connection 비용이 락 자체 비용보다 커져 측정값 왜곡
4. **DB commit 이 Redis unlock 보다 먼저** — commit 전 unlock 하면 다른 스레드가 미커밋 데이터 보고 작업 → 동시성 깨짐. 분산락 학습에서 가장 흔한 실수 (connection close 순서는 unlock 과 무관)

## measurements.md 형식 (1, 2 주차와 일관)

자동 누적 형식 그대로:
```
- [05-19 14:00] s1 · FOR UPDATE 손 측정 (관찰)
- [05-20 22:00] s2 · 비관락 (50스레드 × 200): 누락 0 / 실패 0 / 응답 Xms
- [05-20 22:30] s2 · 낙관락 (50스레드 × 200): 누락 0 / 재시도 X / 응답 Yms
- [05-21 22:00] s2 · 분산락 Redis (50스레드 × 200): 누락 0 / 응답 Zms
- [05-21 23:00] s3 · 충돌 빈도 낮음 (100 row 분산): 비관 Xms / 낙관 Yms / 분산 Zms
- [05-21 23:30] s4 · 데드락 재현 — 횟수 X / abort Y
```

STAGE 3 의 충돌 빈도별 비교 표는 별도 섹션 (`## STAGE 3 — 충돌 빈도별 비교`) 으로 분리.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 3. 동시에 여러 명 흉내내기 (모두 동일 — 1, 2 주차와 같은 패턴)
━━━━━━━━━━━━━━━━━━━━━━━━━━

`ExecutorService` + `CountDownLatch` 그대로. 람다 안에서 본인 락 전략 호출.

```java
ExecutorService executor = Executors.newFixedThreadPool(50);
AtomicInteger success = new AtomicInteger(0);
AtomicInteger retried = new AtomicInteger(0);   // 낙관락 재시도
CountDownLatch start = new CountDownLatch(1);

for (int i = 0; i < 200; i++) {
    executor.submit(() -> {
        try { start.await(); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        // 본인의 락 전략으로 작업 (비관 / 낙관 / 분산)
    });
}
Thread.sleep(50);
start.countDown();
executor.shutdown();
executor.awaitTermination(300, TimeUnit.SECONDS);   // 2주차 교훈: timeout 컷 피하기
```

> ⚠️ **`awaitTermination` 은 충분히 큰 값** (예: 300 초) — 2 주차에서 60 초 timeout 컷이 측정값 왜곡한 사건의 교훈.


━━━━━━━━━━━━━━━━━━━━━━━━━━
환경
━━━━━━━━━━━━━━━━━━━━━━━━━━
- Java 21
- **Spring Boot 안 띄움** — `main()` 메서드 + 순수 JDBC + HikariCP + Lettuce (Redis 클라이언트)
- DB: **PostgreSQL 16** — 루트 `docker-compose.yml` 의 기존 컨테이너 그대로 사용
- 분산락: **Redis 7** — 루트 `docker-compose.yml` 의 `csstudy-redis` 컨테이너
- 동시성 흉내: 1, 2 주차와 동일한 `ExecutorService`

## DB / Redis 띄우기

```bash
# 프로젝트 루트에서
docker compose up -d         # postgres + redis 기동
docker compose down          # 종료
docker compose down -v       # 데이터 볼륨까지 삭제 (완전 초기화)
```

## DB / Redis 접속 정보 (루트 `docker-compose.yml` 기준)

| 항목 | 값 |
|---|---|
| PG host / port | `localhost:5433` (컨테이너 내부는 5432, 호스트는 5433 매핑) |
| PG database | `csstudy` |
| PG user | `csstudy` |
| PG password | `csstudy1234` |
| PG JDBC URL | `jdbc:postgresql://localhost:5433/csstudy` |
| Redis host / port | `localhost:6379` |

```bash
# 접속 확인
docker exec csstudy-postgres psql -U csstudy -d csstudy -c "SELECT 1"
docker exec csstudy-redis redis-cli PING        # → PONG
```

## 본인 도메인 스키마

각자 자기 PC 에서 docker 띄우므로 DB 는 본인 전용. 본인 도메인 테이블은 `csstudy` DB 에 그대로 만든다. **version 컬럼 필수** (낙관적 락용):

```sql
-- 예: 계좌 이체 도메인
CREATE TABLE IF NOT EXISTS account (
    id BIGINT PRIMARY KEY,
    balance NUMERIC NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
INSERT INTO account (id, balance) VALUES (1, 10000), (2, 10000)
ON CONFLICT (id) DO UPDATE SET balance = EXCLUDED.balance, version = 0;
```

> 측정 시작 전 매번 `TRUNCATE` + `INSERT` 로 초기 상태 복원 (STAGE 3 측정 원칙).
> 도메인 / 측정 셋업이 더 꼬이면 `docker compose down -v` 로 볼륨까지 날리고 다시 시작.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STAGE 진행 가이드
━━━━━━━━━━━━━━━━━━━━━━━━━━

## 예상 학습 시간 (한 주 기준)

| 단계 | 시간 | 비고 |
|---|---|---|
| STAGE 1 (손 측정 — psql / redis-cli) | 2~3 시간 | 월요일 전에 끝내면 여유 |
| STAGE 2-1 비관락 | 1~2 시간 | JDBC 익숙하면 빠름 |
| STAGE 2-2 낙관락 | 1~2 시간 | version 컬럼 + 재시도 루프 |
| **STAGE 2-3 분산락** ★ | **3~4 시간** | Redis + Lettuce + Lua 처음이면 부담. 함정 (싱글턴 / TTL) 주의 |
| STAGE 3 충돌 빈도별 측정 | 3~4 시간 | 9 케이스 측정 + 해석 |
| STAGE 4 데드락 + 4 조건 매핑 | 2~3 시간 | 면접 직결 |
| **합계** | **12~18 시간** | |

**배분**:
- 직장인 (평일 저녁 2 시간 × 5 + 주말 8 시간) — 충분
- 학생 (주말 풀타임 2 일) — 충분
- 부담스러우면 **분산락 코드 (STAGE 2-3) 가 가장 무거움** — 시간 부족 시 redis-cli 손 실습 (STAGE 1-3) + 개념만으로도 면접 답변 가능

### [월 11:00 — Draft PR 마감 + 겪기 발표]

#### ▸ STAGE 1 — 두 세션으로 손으로 보기 (필수)

**목표**: `SELECT FOR UPDATE` 가 어떻게 다른 세션을 막는지, 데드락은 어떻게 발생하는지, Redis 분산락은 어떻게 동작하는지 직접 확인.

DBeaver / IntelliJ DB / `psql` 중 편한 도구로 **세션 2 개** 동시에 띄우기.

##### 1-1. FOR UPDATE — row lock 의 동작

| 시나리오 | 시퀀스 |
|---|---|
| **단독 사용** | A: `BEGIN; SELECT * FROM account WHERE id = 1 FOR UPDATE;` → A 가 그 row 잡음 |
| **다른 세션 SELECT** | B: `SELECT * FROM account WHERE id = 1;` (FOR UPDATE 없음) — 어떻게 되는가? |
| **다른 세션 UPDATE** | B: `UPDATE account SET balance = ... WHERE id = 1;` — 어떻게 되는가? |
| **다른 세션 FOR UPDATE** | B: `SELECT * FROM account WHERE id = 1 FOR UPDATE;` — 어떻게 되는가? |
| **다른 세션 FOR SHARE** | B: `SELECT * FROM account WHERE id = 1 FOR SHARE;` — A 가 X-lock 잡고 있으면 S-lock 도 막힘 |
| **A 가 COMMIT 후** | A: `COMMIT;` → B 의 lock 대기 결과는? |

##### 1-2. 데드락 직접 재현 + 4 조건 매핑

세션 2 개로:

```sql
-- 세션 A
BEGIN;
UPDATE account SET balance = balance - 1000 WHERE id = 1;
-- (잠시 멈춤)

-- 세션 B
BEGIN;
UPDATE account SET balance = balance - 1000 WHERE id = 2;
-- (잠시 멈춤)

-- 세션 A
UPDATE account SET balance = balance + 1000 WHERE id = 2;   -- B 의 lock 대기

-- 세션 B
UPDATE account SET balance = balance + 1000 WHERE id = 1;   -- A 의 lock 대기 → 데드락
```

PG 메시지:
```
ERROR:  deadlock detected
DETAIL: Process X waits for ShareLock on transaction Y; ...
```

**관찰 후 직접 매핑**:
- 상호 배제 — 어디서?
- 점유 대기 — 어디서?
- 비선점 — 어디서?
- 순환 대기 — 어디서?
- 식사하는 철학자와의 매핑 — 포크 / 철학자 / 잡는 순서

##### 1-3. Redis 분산락 — SETNX 동작

`redis-cli` 두 창 띄우고:

```redis
# 창 A
SET lock:account:1 "session-A" NX EX 10       -- 잠금 시도 (10초 TTL)
# 응답: OK

# 창 B
SET lock:account:1 "session-B" NX EX 10       -- 같은 키 잠금 시도
# 응답: (nil)  ← 이미 잠겨있어 실패

# 창 A
DEL lock:account:1                             -- 해제

# 창 B
SET lock:account:1 "session-B" NX EX 10       -- 재시도
# 응답: OK
```

→ 10 초 동안 안 풀어도 TTL 로 자동 해제됨을 확인.

##### 1-4. STAGE 1 결과 정리

`measurements.md` 또는 별도 섹션에:
```
## STAGE 1 — DB 락 + 분산락 + 데드락 (직접 관찰)

| 시나리오 | 다른 세션 SELECT | 다른 세션 UPDATE | 다른 세션 FOR UPDATE |
|---|---|---|---|
| A 가 FOR UPDATE 후 | (관찰) | (관찰) | (관찰) |

데드락 시퀀스 재현 — 어느 쪽 abort: (관찰)
4 조건 매핑: (본인 정리)
Redis SETNX 동작 — TTL 자동 해제 확인: (관찰)
```


### [목 11:00 — Ready PR 전환 + 코드 발표] — STAGE 2 + STAGE 3 + STAGE 4

#### ▸ STAGE 2 — Java 코드로 3 가지 락 구현 (필수)

본인 도메인의 race 를 **3 가지 락으로 각각 풀기**.

##### 2-1. 비관적 락 — SELECT FOR UPDATE

```java
public boolean transfer(Connection conn, long fromId, long toId, BigDecimal amount) throws SQLException {
    long lockFirst  = Math.min(fromId, toId);   // 데드락 회피: 작은 id 부터
    long lockSecond = Math.max(fromId, toId);

    try (PreparedStatement ps = conn.prepareStatement(
            "SELECT balance FROM account WHERE id = ? FOR UPDATE")) {
        ps.setLong(1, lockFirst);
        ps.executeQuery();
    }
    try (PreparedStatement ps = conn.prepareStatement(
            "SELECT balance FROM account WHERE id = ? FOR UPDATE")) {
        ps.setLong(1, lockSecond);
        ps.executeQuery();
    }
    // 잠금 후 잔액 검증 + UPDATE
    return true;
}
```

> ⚠️ **데드락 회피**: row 2 개 잡을 땐 항상 **같은 순서** (예: id 작은 것부터). 안 그러면 50% 확률로 데드락 — STAGE 4 에서 일부러 깨고 측정해본다.

##### 2-2. 낙관적 락 — version 컬럼

```java
public boolean transferOptimistic(Connection conn, long fromId, long toId, BigDecimal amount, int maxRetries) throws SQLException {
    for (int attempt = 0; attempt < maxRetries; attempt++) {
        // balance + version 읽기
        BigDecimal fromBalance;
        long fromVersion;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT balance, version FROM account WHERE id = ?")) {
            ps.setLong(1, fromId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                fromBalance = rs.getBigDecimal(1);
                fromVersion = rs.getLong(2);
            }
        }
        // 검증 + 계산

        // version 비교로 UPDATE
        int affected;
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE account SET balance = ?, version = version + 1 WHERE id = ? AND version = ?")) {
            ps.setBigDecimal(1, fromBalance.subtract(amount));
            ps.setLong(2, fromId);
            ps.setLong(3, fromVersion);
            affected = ps.executeUpdate();
        }
        if (affected == 1) return true;          // 성공
        // 0 이면 다른 트랜잭션이 먼저 UPDATE — 재시도
    }
    return false;   // 재시도 한계 초과
}
```

> 충돌 빈도가 높으면 재시도 비용 폭증. `maxRetries` 와 backoff 정책 본인이 결정.

##### 2-3. 분산락 — Redis SETNX + TTL

Lettuce / Jedis 직접 사용 (Redisson 같은 추상화 라이브러리 X — 1, 2 주차의 "직접 손으로" 패턴 일관).

```java
// RedisClientFactory.java — DataSourceFactory 처럼 싱글턴으로 한 번만 생성
public final class RedisClientFactory {
    private static final RedisClient CLIENT = RedisClient.create("redis://localhost:6379");
    public static StatefulRedisConnection<String, String> connect() {
        return CLIENT.connect();   // 매 호출마다 새 connection
    }
    public static void shutdown() { CLIENT.shutdown(); }
}

// 사용 — connection 만 매 요청 마다 (RedisClient 는 재사용)
public boolean transferWithDistributedLock(long userId, ...) {
    String lockKey = "lock:user:" + userId;
    String lockValue = UUID.randomUUID().toString();

    try (StatefulRedisConnection<String, String> conn = RedisClientFactory.connect()) {
        RedisCommands<String, String> redis = conn.sync();

        // SET key value NX EX 5 — 5초 TTL 로 잠금 시도
        String result = redis.set(lockKey, lockValue,
            SetArgs.Builder.nx().ex(5));
        if (!"OK".equals(result)) {
            // 즉시 실패 (fail-fast) 전략 — caller 가 재시도 결정
            // trade-off: 공정성 없음, 높은 contention 에서 starvation 가능.
            // 실무 개선: exponential backoff 또는 Redisson Pub/Sub 방식 (5+ 주차)
            return false;
        }

        try {
            // 잠금 받은 후 실제 작업 (DB 업데이트 등)
        } finally {
            // 안전한 해제 — 본인이 잡은 lock 만 해제 (Lua script 로 get + del 원자화)
            String luaScript = """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                  return redis.call('del', KEYS[1])
                else
                  return 0
                end
                """;
            redis.eval(luaScript, ScriptOutputType.INTEGER,
                new String[]{lockKey}, lockValue);
        }
    }
    return true;
}
```

> ⚠️ **`RedisClient` 는 싱글턴.** 매 요청마다 `RedisClient.create(...)` 호출하면 connection 생성 비용이 락 자체 비용보다 커져서 측정값이 왜곡됨. 2 주차 timeout 컷 같은 측정 도구 결함이 될 수 있음. `connect()` 만 매번, `RedisClient` 는 한 번.

> **왜 Lua script 로 해제?** 단순 `DEL` 하면 TTL 만료 후 다른 트랜잭션이 새 lock 잡은 걸 본인이 풀어버리는 사고 발생. `get + del` 을 원자적으로.


#### ▸ STAGE 3 — 충돌 빈도별 정량 비교 (필수)

##### 3-1. 측정

같은 도메인 / 같은 시도 횟수 / 다른 충돌 빈도로 3 가지 락 비교:

| 충돌 빈도 | 시나리오 | 비관 (FOR UPDATE) | 낙관 (version) | 분산 (Redis) |
|---|---|---|---|---|
| **낮음** | 50 스레드 → 100 row 분산 | (측정) | (측정) | (측정) |
| **중간** | 50 스레드 → 10 row 집중 | (측정) | (측정) | (측정) |
| **높음** | 50 스레드 → 모두 같은 row (또는 계좌 쌍) 집중 | (측정) | (측정) | (측정) |

측정 항목:
- **누락** (Lost Update) — 모두 0 이어야 함 (검증용)
- **재시도 / 실패** — 낙관은 재시도, 분산은 lock 획득 실패
- **starvation** — 낙관락에서 `maxRetries` 초과로 `return false` 한 횟수 (처리 자체가 누락된 작업)
- **응답시간** — 5 회 평균
- **데드락** — 비관락에서만 (`40P01`)

##### 3-2. 해석 — 트레이드오프 매트릭스

| 충돌 빈도 | 어떤 락이 유리한가 | 이유 |
|---|---|---|
| 낮음 | 낙관 | 잠금 비용 0, 재시도 거의 없음 |
| 중간 | (측정으로 결정) | (본인 해석) |
| 높음 | 비관 | 낙관은 재시도 폭증 + **starvation 발생** (maxRetries 초과 → 처리 자체가 누락). 비관은 직렬화로 모든 트랜잭션 처리 보장 |

> ⚠️ **starvation vs Lost Update 차이**: Lost Update 는 "처리는 됐는데 결과가 덮어써짐", starvation 은 "재시도 한계 초과로 처리 자체가 안 됨". 둘 다 측정 시 발견되어야 — `누락` 컬럼 하나만 보면 starvation 을 못 잡음.

**분산락의 자리** — DB 락만으로 못 푸는 케이스를 본인 도메인에 매핑:
- 여러 서비스 인스턴스 환경에서 같은 작업 한 번만?
- DB UPDATE 외에 외부 API 호출 (결제 / SMS / 알림) 직렬화?
- 본인 도메인에서 분산락이 자연스러운 시나리오 1 개 적기


#### ▸ STAGE 4 — 데드락 재현 + 4 조건 매핑 (필수)

**목표**: PG 가 데드락을 어떻게 감지하고 어느 쪽을 abort 하는지 측정. 4 조건과 식사하는 철학자를 본인 도메인에 매핑.

##### 4-1. 일부러 데드락 발생시키는 코드

```java
// row 2 개를 다른 순서로 잡도록 50% 확률 분기
public boolean transferDeadlockProne(...) {
    long lockFirst, lockSecond;
    if (ThreadLocalRandom.current().nextBoolean()) {
        lockFirst = fromId; lockSecond = toId;
    } else {
        lockFirst = toId; lockSecond = fromId;
    }
    // 위와 동일하게 FOR UPDATE 두 번
}
```

##### 4-2. 측정 항목

| 항목 | 의미 |
|---|---|
| **데드락 횟수** | SQLState `40P01` 발생 수 |
| **응답시간** | `데드락 횟수 × deadlock_timeout(1s)` 이 큰 비중인지 |
| **abort 분포** | A vs B 어느 쪽이 자주 죽는지 |

##### 4-3. 4 조건 매핑 + 해결책

본인 코드 + 본인 도메인에 매핑:

| 데드락 4 조건 | 본인 도메인에서 어떻게 나타나는가 |
|---|---|
| 상호 배제 | (본인 적기) |
| 점유 대기 | (본인 적기) |
| 비선점 | (본인 적기) |
| 순환 대기 | (본인 적기) |

**해결책 적용** + 재측정:
- 해결책 1: 락 잡는 순서 통일 (가장 흔함)
- 해결책 2: `lock_timeout` 설정 (PG)
- 해결책 3: 한 번에 모든 row 잡기 (`SELECT ... FOR UPDATE` IN 절)

##### 4-4. 식사하는 철학자 → 본인 도메인

5 명 / 5 포크의 고전 문제를 본인 도메인으로 옮겨 설명. 어느 시나리오가 식사하는 철학자와 같은 구조인가?


### [선택] ▸ STAGE 5 — JPA `@Version` 비교 (4 주차 브릿지)

> ⏰ **언제 하나**: Ready PR (목 11:00) 이후 여유 시 시도. 늦어도 **4 주차 시작 전 (다음 목)** 까지.

JDBC 의 수동 version 처리를 JPA 의 `@Version` 으로 옮기면 어떻게 추상화되는가:

```java
@Entity
class Account {
    @Id Long id;
    BigDecimal balance;
    @Version Long version;
}
```

→ `OptimisticLockException` 발생 시점 / 재시도 정책 / Spring `@Retryable` 와의 조합 — 4 주차로 연결.


━━━━━━━━━━━━━━━━━━━━━━━━━━
금지 키워드 — STAGE 1~2 동안만 (검색 금지)
━━━━━━━━━━━━━━━━━━━━━━━━━━
- `@Transactional` 어노테이션 / Spring `TransactionTemplate` (4 주차 보호)
- Redisson `RLock` / Spring `@DistributedLock` (라이브러리 추상화 — 직접 손으로 SETNX 부터)
- Redlock 알고리즘 / Zookeeper / Etcd (5+ 주차 분산 시스템)
- JPA `@Version` 어노테이션 (STAGE 5 보너스 단계 전까지)


━━━━━━━━━━━━━━━━━━━━━━━━━━
학습 기록 (자유)
━━━━━━━━━━━━━━━━━━━━━━━━━━

### 3주차 참고 질문 (답하고 싶은 만큼만)
- 비관적 락이 격리 수준 (RC) 위에서도 동작하는 메커니즘
- 낙관적 락의 충돌 감지가 `UPDATE` 의 무엇으로 이뤄지는가
- `FOR UPDATE` 와 `FOR SHARE` 차이를 실서비스 예로 1 개
- 데드락 4 조건 — 본인 코드에서 어떤 조건이 깨지는지
- 식사하는 철학자를 본인 도메인으로 1 분 설명
- DB 락만으로 못 풀고 분산락이 필요한 케이스 1 개
- 본인 도메인에서 비관 vs 낙관 어느 쪽을 선택할지 + 본인 측정값 근거
- 2 주차의 SSI (낙관적 직렬화) 와 3 주차의 낙관적 락 (version) 차이

### 면접 단골 + 본인 답
- **"동시 매수 잔고 음수 방지" — 어떻게 구현하나?** (FOR UPDATE / version / 분산락 중 어느 것 + 이유)
- **"Redis 분산락 vs DB 락 차이는?"** (범위 / 해제 / 데드락 감지 / 장애 대응 4 축)
- **"데드락 발생 조건 4 개와 1 개 깨는 방법"** (상호 배제 / 점유 대기 / 비선점 / 순환 대기)

### 실무 확장 화두 (스터디 토론 / 면접 후속 질문 거리)
- **낙관락 타이트 루프**: `continue` 즉시 재시도는 충돌 잦을 때 CPU / DB 폭주. **Exponential backoff + Jitter** 가 표준 — 어떻게 구현?
- **낙관락 partial update**: 두 row UPDATE 중 첫 번째 성공 + 두 번째 실패 시 **`conn.rollback()` 필수** (안 하면 차감만 반영된 채 재시도 → 두 번 차감 위험)
- **분산락 단일 락키 vs MultiLock**: `lock:account:1` 하나로 두 계좌 잠그면 전체 이체 직렬화 → 학습 OK, 실무는 두 락 순서대로 또는 Redisson MultiLock
- **분산락 fail-fast vs 재시도**: 결제처럼 반드시 처리되어야 하는 작업은 backoff 재시도. Redisson Pub/Sub 으로 락 대기 부하 감소

### 락 선택 매트릭스 (면접 답변 기준)

| 상황 | 선택 | 이유 |
|---|---|---|
| 단일 DB, 충돌 빈도 낮음 | 낙관락 (version) | 잠금 비용 0, 재시도 거의 없음 |
| 단일 DB, 충돌 빈도 높음 | 비관락 (FOR UPDATE) | 낙관은 재시도 폭증 + starvation. 비관은 직렬화로 안정 |
| 멀티 인스턴스 / 외부 API 직렬화 | Redis 분산락 | DB 락은 같은 DB 안에서만. 인스턴스 간 / 외부 자원 보호는 분산락 필수 |
| DB 트랜잭션 범위 밖 자원 보호 | Redis 분산락 | 결제 API / 스케줄러 중복 방지 / 파일 처리 등 |


━━━━━━━━━━━━━━━━━━━━━━━━━━
막힐 때
━━━━━━━━━━━━━━━━━━━━━━━━━━
1. 직접 시도 (`CLAUDE.md` 룰)
2. AI 에 물어보기 — 3 단계 힌트만 받음 (코드 직답 X)
3. 디스코드 `#질문` 채널 — 코드 + 에러 + 본인 도메인 schema.sql 함께

특히 **STAGE 1 에서 데드락이 재현 안 되는 경우**: 두 트랜잭션이 정말 다른 세션인지 (PID 다른지), 두 row 를 다른 순서로 잡았는지 확인. 같은 row 면 데드락이 아니라 단순 lock wait.

**Redis 분산락이 안 풀리는 경우**: `SET ... NX EX` 의 `NX` 빠뜨렸는지 (있으면 무조건 덮어씀), TTL 만료됐는데 `DEL` 시도하는지, Lua script 의 KEYS/ARGV 매핑 확인.
