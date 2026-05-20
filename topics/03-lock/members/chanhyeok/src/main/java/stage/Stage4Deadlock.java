package stage;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import infra.DataSourceFactory;
import infra.MeasurementLog;
import infra.SchemaBootstrap;

/**
 * STAGE 4. 데드락 의도적 재현 + Coffman 4 조건 P2P 매핑.
 *
 * <h3>실험 설계 — 락 순서 깨기</h3>
 * 50% 확률로 사용자 1 → 2 송금 vs 2 → 1 송금. 두 트랜잭션이 반대 순서로 row 잡으면 순환 대기 → 데드락.
 *
 * <h3>측정 항목</h3>
 * - 데드락 횟수 (SQLState 40P01) — PG 가 abort 시킨 트랜잭션 수
 * - 응답시간 — 데드락 횟수 × deadlock_timeout (1 초) 누적 영향 큼
 *
 * <h3>Coffman 4 조건 P2P 매핑</h3>
 * <ul>
 *   <li>상호 배제 — user_wallet row 가 X-lock 으로 한 트랜잭션만 보유</li>
 *   <li>점유 대기 — A 가 wallet 1 잡고 wallet 2 대기, B 가 wallet 2 잡고 wallet 1 대기</li>
 *   <li>비선점 — PG 는 자발적 COMMIT 까지 락 안 뺏음</li>
 *   <li>순환 대기 — A → B → A wait cycle 발생</li>
 * </ul>
 */
public class Stage4Deadlock {

    // 데드락 발생 시 deadlock_timeout (1 초) wait 누적되므로 시도 횟수 작게
    private static final int THREADS = 50;
    private static final int ATTEMPTS = 50;
    private static final int RUNS = 3;
    private static final BigDecimal AMOUNT = new BigDecimal("10");

    public static void main(String[] args) throws Exception {
        DataSource ds = DataSourceFactory.create(THREADS);

        SchemaBootstrap.reset(ds);

        System.out.println("[워밍업] 1 회");
        runOnce(ds);

        double totalDeadlocks = 0, totalMillis = 0;
        for (int run = 0; run < RUNS; run++) {
            RunResult r = runOnce(ds);
            System.out.printf("  RUN %d: deadlocks=%d / %.1f ms%n",
                run, r.deadlocks, r.millis);
            totalDeadlocks += r.deadlocks;
            totalMillis += r.millis;
        }

        double avgDeadlocks = totalDeadlocks / RUNS;
        double avgMillis = totalMillis / RUNS;

        System.out.println();
        System.out.printf("=== STAGE 4 데드락 재현 P2P 송금 — %d 스레드 × %d 시도 × %d 회 평균 ===%n",
            THREADS, ATTEMPTS, RUNS);
        System.out.printf("데드락 횟수 %.1f / 응답 %.1f ms%n", avgDeadlocks, avgMillis);
        System.out.println();
        System.out.println("Coffman 4 조건 P2P 매핑:");
        System.out.println("  - 상호 배제: user_wallet row 가 X-lock 으로 한 트랜잭션만 보유");
        System.out.println("  - 점유 대기: A 가 wallet 1 잡고 wallet 2 대기 / B 가 wallet 2 잡고 wallet 1 대기");
        System.out.println("  - 비선점:   PG 는 자발적 COMMIT 까지 락 안 뺏음");
        System.out.println("  - 순환 대기: A→B→A wait cycle → deadlock_timeout (1s) 후 victim abort");

        MeasurementLog.save("s4", "데드락 재현 (P2P)", 0, avgDeadlocks, avgMillis);

        DataSourceFactory.close(ds);
    }

    private static RunResult runOnce(DataSource ds) throws Exception {
        SchemaBootstrap.reset(ds);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger deadlocks = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < ATTEMPTS; i++) {
            executor.submit(() -> {
                try { start.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                // 50% 확률로 락 순서 다르게 → 데드락 강제 발생
                long lockFirst, lockSecond;
                if (ThreadLocalRandom.current().nextBoolean()) {
                    lockFirst = 1L; lockSecond = 2L;
                } else {
                    lockFirst = 2L; lockSecond = 1L;
                }

                Connection conn = null;
                try {
                    conn = ds.getConnection();
                    conn.setAutoCommit(false);

                    // 첫 번째 row 잠금
                    forUpdate(conn, lockFirst);
                    // 두 번째 row 잠금 — 반대 순서면 데드락
                    forUpdate(conn, lockSecond);

                    // 데드락 재현 전용 — 잔액 검증 생략 (balance 음수 가능, 의도된 동작)
                    update(conn, lockFirst, AMOUNT.negate());
                    update(conn, lockSecond, AMOUNT);

                    conn.commit();
                } catch (SQLException e) {
                    if ("40P01".equals(e.getSQLState())) {
                        deadlocks.incrementAndGet();
                    }
                    if (conn != null) try { conn.rollback(); } catch (SQLException ignore) {}
                } finally {
                    if (conn != null) try {
                        conn.setAutoCommit(true);
                        conn.close();
                    } catch (SQLException ignore) {}
                }
            });
        }

        Thread.sleep(50);
        long t0 = System.nanoTime();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(300, TimeUnit.SECONDS);
        double millis = (System.nanoTime() - t0) / 1_000_000.0;

        return new RunResult(deadlocks.get(), millis);
    }

    private static void forUpdate(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT balance FROM user_wallet WHERE id = ? FOR UPDATE")) {
            ps.setLong(1, id);
            ps.executeQuery();
        }
    }

    private static void update(Connection conn, long id, BigDecimal delta) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE user_wallet SET balance = balance + ? WHERE id = ?")) {
            ps.setBigDecimal(1, delta);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private record RunResult(int deadlocks, double millis) {}
}
