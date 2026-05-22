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
 * STAGE 2-2. 낙관적 락 (version 컬럼) — P2P 송금.
 *
 * <h3>실험 설계</h3>
 * 50 스레드 × 200 시도. 잠그지 않고 3 row 의 version 비교 후 UPDATE.
 * 충돌 시 재시도 (최대 100 회). 한계 초과 = starvation.
 *
 * <h3>P2P 핵심 — fee_revenue 핫스팟 영향</h3>
 * 모든 송금이 같은 row (fee_revenue id=1) 를 건드림 → 충돌 빈도 극단적.
 * 1 주차 쿠폰 (단일 row) 과 유사한 starvation 가능성.
 */
public class Stage2Optimistic {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 200;
    private static final int RUNS = 5;
    private static final int MAX_RETRIES = 10;
    private static final long FROM_ID = 1L;
    private static final long TO_ID = 2L;
    private static final BigDecimal AMOUNT = new BigDecimal("10");
    private static final BigDecimal INITIAL_TOTAL = BigDecimal.valueOf(2_000_000);

    public static void main(String[] args) throws Exception {
        DataSource ds = DataSourceFactory.create(THREADS);
        P2PWallet wallet = new P2PWallet();

        System.out.println("[워밍업] 1 회");
        runOnce(ds, wallet);

        double totalMisses = 0, totalStarved = 0, totalMillis = 0;
        for (int run = 0; run < RUNS; run++) {
            RunResult r = runOnce(ds, wallet);
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
        System.out.printf("=== STAGE 2-2 낙관적 락 P2P 송금 — %d 스레드 × %d 시도 × %d 회 평균 ===%n",
            THREADS, ATTEMPTS, RUNS);
        System.out.printf("누락 %.1f / starvation %.1f / 응답 %.1f ms%n",
            avgMisses, avgStarved, avgMillis);

        MeasurementLog.save("s2-2", "낙관락 version (P2P)", avgMisses, avgStarved, avgMillis);

        DataSourceFactory.close(ds);
    }

    private static RunResult runOnce(DataSource ds, P2PWallet wallet) throws Exception {
        SchemaBootstrap.reset(ds);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
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
                    boolean ok = wallet.transferOptimistic(conn, FROM_ID, TO_ID, AMOUNT, MAX_RETRIES);
                    conn.commit();
                    if (!ok) starved.incrementAndGet();
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

        BigDecimal total;
        try (Connection conn = ds.getConnection()) {
            total = wallet.balanceOf(conn, FROM_ID)
                .add(wallet.balanceOf(conn, TO_ID))
                .add(wallet.feeTotal(conn));
        }
        int misses = total.compareTo(INITIAL_TOTAL) == 0 ? 0 : 1;

        return new RunResult(misses, starved.get(), millis);
    }

    private record RunResult(int misses, int starved, double millis) {}
}
