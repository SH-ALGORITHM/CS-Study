package stage;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import domain.P2PWallet;
import infra.DataSourceFactory;
import infra.MeasurementLog;
import infra.SchemaBootstrap;

/**
 * STAGE 2-1. 비관적 락 (SELECT FOR UPDATE) — P2P 송금.
 *
 * <h3>실험 설계</h3>
 * 50 스레드가 동시에 사용자 1 → 2 송금. row 3 개 (송금자 / 수취자 / fee_revenue) 를
 * {@code min(from,to) → max(from,to) → fee_revenue(id=1)} 순서로 잠근다.
 *
 * <h3>검증 — 원금 보존</h3>
 * 송금이 어떻게 도더라도 전체 통화량은 일정해야 한다:
 * <pre>
 *   wallet1.balance + wallet2.balance + fee_revenue.total_collected = 2,000,000 (초기 user_wallet 합)
 * </pre>
 * 합이 다르면 누락 (lost update / partial commit).
 */
public class Stage2Pessimistic {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 200;
    private static final int RUNS = 5;
    private static final long FROM_ID = 1L;
    private static final long TO_ID = 2L;
    private static final BigDecimal AMOUNT = new BigDecimal("10");
    private static final BigDecimal INITIAL_TOTAL = BigDecimal.valueOf(2_000_000);

    public static void main(String[] args) throws Exception {
        DataSource ds = DataSourceFactory.create(THREADS);
        P2PWallet wallet = new P2PWallet();

        System.out.println("[워밍업] 1 회");
        runOnce(ds, wallet);

        double totalMisses = 0, totalFailed = 0, totalMillis = 0;
        for (int run = 0; run < RUNS; run++) {
            RunResult r = runOnce(ds, wallet);
            System.out.printf("  RUN %d: misses=%d / failed=%d / %.1f ms%n",
                run, r.misses, r.failed, r.millis);
            totalMisses += r.misses;
            totalFailed += r.failed;
            totalMillis += r.millis;
        }

        double avgMisses = totalMisses / RUNS;
        double avgFailed = totalFailed / RUNS;
        double avgMillis = totalMillis / RUNS;

        System.out.println();
        System.out.printf("=== STAGE 2-1 비관적 락 P2P 송금 — %d 스레드 × %d 시도 × %d 회 평균 ===%n",
            THREADS, ATTEMPTS, RUNS);
        System.out.printf("누락 %.1f / 실패 %.1f / 응답 %.1f ms%n",
            avgMisses, avgFailed, avgMillis);

        MeasurementLog.save("s2-1", "비관락 FOR UPDATE (P2P)", avgMisses, avgFailed, avgMillis);

        DataSourceFactory.close(ds);
    }

    private static RunResult runOnce(DataSource ds, P2PWallet wallet) throws Exception {
        SchemaBootstrap.reset(ds);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger failed = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < ATTEMPTS; i++) {
            executor.submit(() -> {
                try { start.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                Connection conn = null;
                try {
                    conn = ds.getConnection();
                    conn.setAutoCommit(false);
                    wallet.transferPessimistic(conn, FROM_ID, TO_ID, AMOUNT);
                    conn.commit();
                } catch (SQLException e) {
                    failed.incrementAndGet();
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

        BigDecimal total;
        try (Connection conn = ds.getConnection()) {
            total = wallet.balanceOf(conn, FROM_ID)
                       .add(wallet.balanceOf(conn, TO_ID))
                       .add(wallet.feeTotal(conn));
        }
        int misses = total.compareTo(INITIAL_TOTAL) == 0 ? 0 : 1;

        return new RunResult(misses, failed.get(), millis);
    }

    private record RunResult(int misses, int failed, double millis) {}
}
