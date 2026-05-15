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

import domain.BankAccount;
import infra.DataSourceFactory;
import infra.MeasurementLog;
import infra.SchemaBootstrap;

/**
 * STAGE 2-2. 낙관적 락 (version 컬럼).
 *
 * <p>잠그지 않고 version 비교 후 UPDATE. 충돌 시 재시도.
 * 충돌 빈도 높으면 starvation (재시도 한계 초과) 가능.
 */
public class Stage2Optimistic {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 200;
    private static final int RUNS = 5;
    private static final int MAX_RETRIES = 100;
    private static final long FROM_ID = 1L;
    private static final long TO_ID = 2L;
    private static final BigDecimal AMOUNT = new BigDecimal("10");

    public static void main(String[] args) throws Exception {
        DataSource ds = DataSourceFactory.create(THREADS);
        BankAccount bank = new BankAccount();

        System.out.println("[워밍업] 1 회");
        runOnce(ds, bank);

        double totalMisses = 0, totalStarved = 0, totalMillis = 0;
        for (int run = 0; run < RUNS; run++) {
            RunResult r = runOnce(ds, bank);
            System.out.printf("  RUN %d: misses=%d / starved=%d / %.1f ms%n",
                run, r.misses, r.starved, r.millis);
            totalMisses += r.misses;
            totalStarved += r.starved;
            totalMillis += r.millis;
        }

        double avgMisses = totalMisses / RUNS;
        double avgStarved = totalStarved / RUNS;
        double avgMillis = totalMillis / RUNS;

        System.out.println();
        System.out.printf("=== STAGE 2-2 낙관적 락 (version) — %d 스레드 × %d 시도 × %d 회 평균 ===%n",
            THREADS, ATTEMPTS, RUNS);
        System.out.printf("누락 %.1f / starvation %.1f / 응답 %.1f ms%n",
            avgMisses, avgStarved, avgMillis);

        MeasurementLog.save("s2-2", "낙관락 version", avgMisses, avgStarved, avgMillis);

        DataSourceFactory.close(ds);
    }

    private static RunResult runOnce(DataSource ds, BankAccount bank) throws Exception {
        SchemaBootstrap.resetAccounts(ds);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger starved = new AtomicInteger(0);
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

                    boolean ok = bank.transferOptimistic(conn, FROM_ID, TO_ID, AMOUNT, MAX_RETRIES);
                    conn.commit();
                    if (ok) success.incrementAndGet();
                    else starved.incrementAndGet();
                } catch (SQLException e) {
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

        BigDecimal sum;
        try (Connection conn = ds.getConnection()) {
            sum = bank.getBalance(conn, FROM_ID).add(bank.getBalance(conn, TO_ID));
        }
        int misses = sum.compareTo(new BigDecimal("20000")) == 0 ? 0 : 1;

        return new RunResult(misses, starved.get(), millis);
    }

    private record RunResult(int misses, int starved, double millis) {}
}
