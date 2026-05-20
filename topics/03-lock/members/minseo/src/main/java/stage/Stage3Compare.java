package stage;

import domain.SeatBooking;
import infra.DataSourceFactory;
import infra.MeasurementLog;
import infra.RedisClientFactory;
import infra.SchemaBootstrap;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * STAGE 3. 3 가지 락 정량 비교 (같은 조건 - 50 스레드 × 200 시도).
 */
public class Stage3Compare {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 200;
    private static final int RUNS = 5;
    private static final int MAX_RETRIES = 100;
    private static final long SEAT_ID = 1L;
    private static final String USER_ID = "UserA";
    private static final BigDecimal PRICE = new BigDecimal("1000");

    private static final String UNLOCK_LUA = """
        if redis.call('get', KEYS[1]) == ARGV[1] then
          return redis.call('del', KEYS[1])
        else
          return 0
        end
        """;

    public static void main(String[] args) throws Exception {
        DataSource ds = DataSourceFactory.create(THREADS);
        SeatBooking booking = new SeatBooking();

        System.out.println("[워밍업] 1 회");
        measure(ds, booking, "warmup", Strategy.PESSIMISTIC, 1);

        Result pessimistic = measure(ds, booking, "비관 FOR UPDATE", Strategy.PESSIMISTIC, RUNS);
        Result optimistic  = measure(ds, booking, "낙관 version",   Strategy.OPTIMISTIC, RUNS);
        Result distributed = measure(ds, booking, "분산 Redis",     Strategy.DISTRIBUTED, RUNS);

        System.out.println();
        System.out.printf("=== STAGE 3 — 3 가지 락 비교 (%d 스레드 × %d 시도 × %d 회 평균) ===%n",
            THREADS, ATTEMPTS, RUNS);
        System.out.println();
        System.out.println("| 락 종류        | 누락 (평균) | 실패/재시도 | 응답시간 (ms) |");
        System.out.println("|---------------|------------|------------|--------------|");
        printRow("비관 FOR UPDATE", pessimistic);
        printRow("낙관 version",    optimistic);
        printRow("분산 Redis",      distributed);

        MeasurementLog.save("s3", "비관 FOR UPDATE", pessimistic.misses, pessimistic.failed, pessimistic.millis);
        MeasurementLog.save("s3", "낙관 version",    optimistic.misses,  optimistic.failed,  optimistic.millis);
        MeasurementLog.save("s3", "분산 Redis",      distributed.misses, distributed.failed, distributed.millis);

        DataSourceFactory.close(ds);
        RedisClientFactory.shutdown();
    }

    private static void printRow(String name, Result r) {
        System.out.printf("| %-13s | %10.1f | %10.1f | %12.1f |%n", name, r.misses, r.failed, r.millis);
    }

    private static Result measure(DataSource ds, SeatBooking booking, String label, Strategy strategy, int runs)
            throws Exception {
        double totalMisses = 0, totalFailed = 0, totalMillis = 0;
        for (int run = 0; run < runs; run++) {
            RunResult r = runOnce(ds, booking, strategy);
            if (runs > 1) {
                System.out.printf("  [%s] RUN %d: misses=%d / failed=%d / %.1f ms%n",
                    label, run, r.misses, r.failed, r.millis);
            }
            totalMisses += r.misses;
            totalFailed += r.failed;
            totalMillis += r.millis;
        }
        return new Result(totalMisses / runs, totalFailed / runs, totalMillis / runs);
    }

    private static RunResult runOnce(DataSource ds, SeatBooking booking, Strategy strategy) throws Exception {
        SchemaBootstrap.resetAlls(ds);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);

        String lockKey = "lock:seat:" + SEAT_ID;

        for (int i = 0; i < ATTEMPTS; i++) {
            executor.submit(() -> {
                try { start.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                switch (strategy) {
                    case PESSIMISTIC -> runPessimistic(ds, booking, success, failed);
                    case OPTIMISTIC  -> runOptimistic(ds, booking, success, failed);
                    case DISTRIBUTED -> runDistributed(ds, booking, success, failed, lockKey);
                }
            });
        }

        Thread.sleep(50);
        long t0 = System.nanoTime();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(300, TimeUnit.SECONDS);
        double millis = (System.nanoTime() - t0) / 1_000_000.0;

        int misses = 0;
        try (Connection conn = ds.getConnection()) {
            String reservedBy = booking.getReservedBy(conn, SEAT_ID);
            BigDecimal balance = booking.getWalletBalance(conn, USER_ID);

            if (reservedBy == null || balance.compareTo(new BigDecimal("99000")) != 0) {
                misses = 1;
            }
            if (success.get() != 1) {
                // 분산락의 경우 락실패가 많으면 success 가 0일 수 있으나, 여기서는 contention 비교용이므로 체크
                if (strategy != Strategy.DISTRIBUTED || failed.get() < ATTEMPTS) {
                    misses = 1;
                }
            }
        }

        return new RunResult(misses, failed.get(), millis);
    }

    private static void runPessimistic(DataSource ds, SeatBooking booking, AtomicInteger success, AtomicInteger failed) {
        Connection conn = null;
        try {
            conn = ds.getConnection();
            conn.setAutoCommit(false);
            boolean ok = booking.bookSeatPessimistic(conn, SEAT_ID, USER_ID, PRICE);
            conn.commit();
            if (ok) success.incrementAndGet();
        } catch (SQLException e) {
            failed.incrementAndGet();
            if (conn != null) try { conn.rollback(); } catch (SQLException ignore) {}
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignore) {}
        }
    }

    private static void runOptimistic(DataSource ds, SeatBooking booking, AtomicInteger success, AtomicInteger starved) {
        Connection conn = null;
        try {
            conn = ds.getConnection();
            conn.setAutoCommit(false);
            boolean ok = booking.bookSeatOptimistic(conn, SEAT_ID, USER_ID, PRICE, MAX_RETRIES);
            conn.commit();
            if (ok) success.incrementAndGet();
            else starved.incrementAndGet();
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignore) {}
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignore) {}
        }
    }

    private static void runDistributed(DataSource ds, SeatBooking booking, AtomicInteger success, AtomicInteger lockFailed, String lockKey) {
        String lockValue = UUID.randomUUID().toString();
        try (StatefulRedisConnection<String, String> rconn = RedisClientFactory.connect()) {
            RedisCommands<String, String> redis = rconn.sync();
            String result = redis.set(lockKey, lockValue, SetArgs.Builder.nx().ex(5));
            if (!"OK".equals(result)) {
                lockFailed.incrementAndGet();
                return;
            }
            try (Connection conn = ds.getConnection()) {
                conn.setAutoCommit(false);
                boolean ok = booking.bookSeatRaw(conn, SEAT_ID, USER_ID, PRICE);
                conn.commit();
                if (ok) success.incrementAndGet();
                conn.setAutoCommit(true);
            } catch (SQLException ignore) {
            } finally {
                redis.eval(UNLOCK_LUA, ScriptOutputType.INTEGER, new String[]{lockKey}, lockValue);
            }
        }
    }

    private enum Strategy { PESSIMISTIC, OPTIMISTIC, DISTRIBUTED }

    private record RunResult(int misses, int failed, double millis) {}
    private record Result(double misses, double failed, double millis) {}
}
