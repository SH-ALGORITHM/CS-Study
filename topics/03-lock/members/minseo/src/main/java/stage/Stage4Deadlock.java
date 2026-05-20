package stage;

import infra.DataSourceFactory;
import infra.MeasurementLog;
import infra.SchemaBootstrap;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * STAGE 4. 데드락 직접 재현 + Coffman 4 조건 매핑.
 *
 * <h3>의도적으로 락 잡는 순서를 깬다</h3>
 * 50% 확률로 (seat 1 -> seat 2) vs (seat 2 -> seat 1) 순서로 FOR UPDATE.
 * 두 트랜잭션이 반대 순서로 잡으면 순환 대기 -> 데드락.
 */
public class Stage4Deadlock {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 50;
    private static final int RUNS = 3;

    public static void main(String[] args) throws Exception {
        DataSource ds = DataSourceFactory.create(THREADS);

        SchemaBootstrap.resetAlls(ds);

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
        System.out.println("  - 상호 배제: seat row 가 X-lock 으로 한 번에 한 트랜잭션만 잡을 수 있음");
        System.out.println("  - 점유 대기: A 가 seat 1 잡고 seat 2 기다림, B 가 seat 2 잡고 seat 1 기다림");
        System.out.println("  - 비선점:   PG 는 트랜잭션이 자발적으로 풀 때까지 lock 안 뺏음");
        System.out.println("  - 순환 대기: A->B->A 의 wait cycle 발생 -> PG 가 deadlock_timeout 후 한쪽 abort");

        MeasurementLog.save("s4", "데드락 재현 (락 순서 깸)", 0, avgDeadlocks, avgMillis);

        DataSourceFactory.close(ds);
    }

    private static RunResult runOnce(DataSource ds) throws Exception {
        SchemaBootstrap.resetAlls(ds);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger deadlocks = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < ATTEMPTS; i++) {
            executor.submit(() -> {
                try { start.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

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

                    // 업데이트 (예약자 등록 시뮬레이션)
                    update(conn, lockFirst, "DeadlockTest");
                    update(conn, lockSecond, "DeadlockTest");

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
                "SELECT id FROM seat WHERE id = ? FOR UPDATE")) {
            ps.setLong(1, id);
            ps.executeQuery();
        }
    }

    private static void update(Connection conn, long id, String userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE seat SET reserved_by = ? WHERE id = ?")) {
            ps.setString(1, userId);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private record RunResult(int deadlocks, double millis) {}
}
