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
 * STAGE 4. 데드락 직접 재현 + Coffman 4 조건 매핑.
 *
 * <h3>의도적으로 락 잡는 순서를 깬다</h3>
 * 50% 확률로 (id=1 → id=2) vs (id=2 → id=1) 순서로 FOR UPDATE.
 * 두 트랜잭션이 반대 순서로 잡으면 순환 대기 → 데드락.
 *
 * <h3>측정 항목</h3>
 * - 데드락 횟수 (SQLState 40P01) — PG 가 abort 시킨 트랜잭션 수
 * - 응답시간 — `데드락 횟수 × deadlock_timeout(1s)` 이 큰 비중인지
 */
public class Stage4Deadlock {

    // 데드락 발생 시 PG deadlock_timeout (1초) wait 가 누적되므로 시도 횟수 작게.
    // 200 시도면 워밍업 1 회만 5분 넘김. 50 으로 줄이면 한 RUN 약 30 초.
    private static final int THREADS = 50;
    private static final int ATTEMPTS = 50;
    private static final int RUNS = 3;
    private static final BigDecimal AMOUNT = new BigDecimal("10");

    public static void main(String[] args) throws Exception {
        DataSource ds = DataSourceFactory.create(THREADS);

        SchemaBootstrap.resetAccounts(ds);

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
        System.out.printf("=== STAGE 4 — 데드락 재현 (락 순서 일부러 깸) — %d 스레드 × %d 시도 × %d 회 평균 ===%n",
            THREADS, ATTEMPTS, RUNS);
        System.out.printf("데드락 횟수 %.1f / 응답 %.1f ms%n", avgDeadlocks, avgMillis);
        System.out.println();
        System.out.println("Coffman 4 조건 매핑 (본인 정리):");
        System.out.println("  - 상호 배제: account row 가 X-lock 으로 한 번에 한 트랜잭션만 잡을 수 있음");
        System.out.println("  - 점유 대기: A 가 id=1 잡고 id=2 기다림, B 가 id=2 잡고 id=1 기다림");
        System.out.println("  - 비선점:   PG 는 트랜잭션이 자발적으로 풀 때까지 lock 안 뺏음");
        System.out.println("  - 순환 대기: A→B→A 의 wait cycle 발생 → PG 가 deadlock_timeout 후 한쪽 abort");

        MeasurementLog.save("s4", "데드락 재현 (락 순서 깸)", 0, avgDeadlocks, avgMillis);

        DataSourceFactory.close(ds);
    }

    private static RunResult runOnce(DataSource ds) throws Exception {
        SchemaBootstrap.resetAccounts(ds);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger deadlocks = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < ATTEMPTS; i++) {
            executor.submit(() -> {
                try { start.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                // 50% 확률로 락 잡는 순서 다르게
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

                    // ⚠️ 데드락 재현 전용 — 잔액 검증 생략. balance 음수 가능 (의도된 동작)
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
                "SELECT balance FROM account WHERE id = ? FOR UPDATE")) {
            ps.setLong(1, id);
            ps.executeQuery();
        }
    }

    private static void update(Connection conn, long id, BigDecimal delta) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE account SET balance = balance + ? WHERE id = ?")) {
            ps.setBigDecimal(1, delta);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private record RunResult(int deadlocks, double millis) {}
}
