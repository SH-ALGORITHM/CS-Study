package stage;

import domain.CurrencyExchange;
import infra.DataSourceFactory;
import infra.MeasurementLog;
import infra.SchemaBootstrap;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * STAGE 2-1 — JDBC 트랜잭션을 손으로 다루기 (헬퍼 없음).
 *
 * ──────────────────────────────────────────────────────
 * 무엇을 하는 코드인가
 * ──────────────────────────────────────────────────────
 * 200명이 동시에 user_id=1의 KRW 잔고에서 1000원씩 환전 시도.
 * READ_COMMITTED + RMW(read-modify-write) 패턴이라 Lost Update 발생.
 *
 * ──────────────────────────────────────────────────────
 * 1주차와 비교
 * ──────────────────────────────────────────────────────
 *   1주차: 200 스레드 → count++ (메모리) → race condition
 *   2주차: 200 스레드 → SELECT→계산→UPDATE (DB) → Lost Update
 *
 *   구조가 동일:
 *     READ (값 읽기) → COMPUTE (계산) → WRITE (쓰기)
 *     이 사이에 다른 스레드/트랜잭션이 끼어들면 결과 오염.
 *
 * ──────────────────────────────────────────────────────
 * 코드 흐름
 * ──────────────────────────────────────────────────────
 *   1. HikariCP로 커넥션 풀 생성 (50개)
 *   2. wallet 테이블 초기화 (KRW 1,000,000 / USD 1,000)
 *   3. CountDownLatch로 200 스레드 동시 출발
 *   4. 각 스레드:
 *      - 커넥션 꺼내기
 *      - setAutoCommit(false)  ← 이거 안 하면 매 SQL이 즉시 commit
 *      - setTransactionIsolation(READ_COMMITTED)
 *      - withdrawKrw(conn, 1000)  ← SELECT → 계산 → UPDATE (RMW)
 *      - commit
 *   5. 전부 끝나면 최종 잔고 확인 → Lost Update 계산
 *
 * ──────────────────────────────────────────────────────
 * Lost Update 계산 방법
 * ──────────────────────────────────────────────────────
 *   성공 카운트: 스레드가 "환전 성공"이라고 센 횟수
 *   실제 차감: 초기 잔고 - 최종 잔고
 *   Lost Update = 성공 카운트에서 기대한 차감 - 실제 차감
 *
 *   예: 성공 200회 × 1000원 = 200,000원 빠져야 하는데
 *       실제로 180,000원만 빠짐 → 20건 Lost Update
 *
 * ──────────────────────────────────────────────────────
 * try-catch-finally 패턴 설명
 * ──────────────────────────────────────────────────────
 *   conn.setAutoCommit(false)  → "내가 직접 commit/rollback 관리할게"
 *   conn.commit()              → 성공하면 DB에 반영
 *   conn.rollback()            → 실패하면 되돌리기
 *   conn.setAutoCommit(true)   → 풀에 반납 전 원상복구 (안 하면 다음 사용자 영향)
 *   conn.close()               → HikariCP에 커넥션 반납 (진짜 닫는 게 아님)
 *
 *   이 패턴이 반복되는 게 보일 것 → Stage 2-2에서 헬퍼로 추출.
 *   그 헬퍼가 결국 4주차 @Transactional이 자동화하는 것.
 */
public class Stage2RaceJdbc {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 200;
    private static final long INITIAL_KRW = 1000000;
    private static final long INITIAL_USD = 1000;
    private static final long USER_ID = 1L;
    private static final long EXCHANGE_AMOUNT = 1000;

    public static void main(String[] args) throws Exception {
        MeasurementLog.setAnchorClass(Stage2RaceJdbc.class);

        // 커넥션 풀 생성
        // HikariCP가 PG에 TCP 커넥션 50개를 미리 만들어둠.
        // 각 커넥션 = PG backend 프로세스 1개 (fork()).
        DataSource ds = DataSourceFactory.create(THREADS);

        // 테이블 초기화
        SchemaBootstrap.resetWallet(ds, USER_ID, INITIAL_KRW, INITIAL_USD);

        CurrencyExchange exchange = new CurrencyExchange(USER_ID);

        // 동시 출발 준비
        // CountDownLatch: 모든 스레드가 준비된 후 한 번에 출발시키는 장치
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < ATTEMPTS; i++) {
            executor.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { return; }

                Connection conn = null;
                try {
                    conn = ds.getConnection();

                    // autoCommit=false: BEGIN 과 같은 효과.
                    // 이걸 안 하면 SELECT, UPDATE가 각각 즉시 commit됨 -> 트랜잭션으로 묶이지 않음 -> 의미 없음.
                    conn.setAutoCommit(false);

                    // 격리 수준 설정: 커넥션 단위
                    conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                    boolean ok = exchange.withdrawKrw(conn, EXCHANGE_AMOUNT);

                    // commit: WAL에 기록 -> fsync -> 디스크 확정
                    conn.commit();
                    if (ok) success.incrementAndGet();

                } catch (SQLException e) {
                    failed.incrementAndGet();
                    if (conn != null) {
                        try { conn.rollback(); } catch (SQLException ignore) {}
                    }
                } finally {
                    if (conn != null) {
                        try {
                            conn.setAutoCommit(true);
                            conn.close();
                        } catch (SQLException ignore) {}
                    }
                }
            });
        }

        // 동시 출발 + 완료 대기
        Thread.sleep(50);  // 모든 스레드가 latch.await()에 도달할 시간
        long t0 = System.nanoTime();
        start.countDown();  // 200 스레드 동시 출발
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        double millis = (System.nanoTime() - t0) / 1_000_000.0;

        // 결과 측정 (측정 끝난 후에만 출력)
        long finalKrw;
        try (Connection c = ds.getConnection()) {
            finalKrw = exchange.getBalance(c, "KRW");
        }
        long actualWithdrawn = INITIAL_KRW - finalKrw;
        long expectedWithdrawn = success.get() * EXCHANGE_AMOUNT;
        long lostUpdates = Math.max(0, expectedWithdrawn - actualWithdrawn) / EXCHANGE_AMOUNT;

        System.out.println("=== STAGE 2-1: 환전 race 재현 (READ_COMMITTED + RMW) ===");
        System.out.printf("환전 시도: %d회 (1000원 × %d)%n", ATTEMPTS, ATTEMPTS);
        System.out.printf("성공 카운트: %d, 실패: %d%n", success.get(), failed.get());
        System.out.printf("기대 차감: %,d원 (성공 %d × %,d원)%n", expectedWithdrawn, success.get(), EXCHANGE_AMOUNT);
        System.out.printf("실제 차감: %,d원 (최종 잔고: %,d원)%n", actualWithdrawn, finalKrw);
        System.out.printf("Lost Update: %d건%n", lostUpdates);
        System.out.printf("응답시간: %.1fms%n", millis);

        if (lostUpdates > 0) {
            System.out.println();
            System.out.println("→ Lost Update 발생!");
            System.out.println("→ SELECT(lock-free)와 UPDATE 사이에 다른 tx가 끼어들어 값을 덮어씀");
            System.out.println("→ 1주차 count++ race와 동일한 구조 (READ → COMPUTE → WRITE gap)");
        }

        MeasurementLog.save("s2-1", "READ_COMMITTED RMW", lostUpdates, failed.get(), millis);

        DataSourceFactory.close(ds);
    }
}
