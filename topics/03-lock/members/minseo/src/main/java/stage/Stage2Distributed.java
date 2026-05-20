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
 * STAGE 2-3. 분산락 (Redis SETNX + TTL + Lua script).
 *
 * <h3>핵심 패턴</h3>
 * 1. SET key value NX EX 5 — 원자적 잠금 + 5초 TTL
 * 2. fail-fast — 락 획득 실패 시 즉시 false (재시도 안 함, caller 책임)
 * 3. Lua script 로 안전 해제 — get + del 원자화
 *
 * <h3>측정값 해석</h3>
 * - 누락: 락 잘 동작했으면 0
 * - 락실패: 락 획득 실패 횟수 (다른 트랜잭션이 보유 중)
 */
public class Stage2Distributed {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 200;
    private static final int RUNS = 5;
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
        runOnce(ds, booking);

        double totalMisses = 0, totalLockFailed = 0, totalMillis = 0;
        for (int run = 0; run < RUNS; run++) {
            RunResult r = runOnce(ds, booking);
            System.out.printf("  RUN %d: misses=%d / lockFailed=%d / %.1f ms%n",
                run, r.misses, r.lockFailed, r.millis);
            totalMisses += r.misses;
            totalLockFailed += r.lockFailed;
            totalMillis += r.millis;
        }

        double avgMisses = totalMisses / RUNS;
        double avgLockFailed = totalLockFailed / RUNS;
        double avgMillis = totalMillis / RUNS;

        System.out.println();
        System.out.printf("=== STAGE 2-3 분산락 (Redis SETNX) — %d 스레드 × %d 시도 × %d 회 평균 ===%n",
            THREADS, ATTEMPTS, RUNS);
        System.out.printf("누락 %.1f / 락실패 %.1f / 응답 %.1f ms%n",
            avgMisses, avgLockFailed, avgMillis);

        MeasurementLog.save("s2-3", "분산락 Redis SETNX", avgMisses, avgLockFailed, avgMillis);

        DataSourceFactory.close(ds);
        RedisClientFactory.shutdown();
    }

    private static RunResult runOnce(DataSource ds, SeatBooking booking) throws Exception {
        SchemaBootstrap.resetAlls(ds);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger lockFailed = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);

        String lockKey = "lock:seat:" + SEAT_ID;

        for (int i = 0; i < ATTEMPTS; i++) {
            executor.submit(() -> {
                try { start.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                String lockValue = UUID.randomUUID().toString();

                try (StatefulRedisConnection<String, String> rconn = RedisClientFactory.connect()) {
                    RedisCommands<String, String> redis = rconn.sync();

                    // 1. 잠금 시도 (NX EX 5초)
                    String result = redis.set(lockKey, lockValue, SetArgs.Builder.nx().ex(5));
                    if (!"OK".equals(result)) {
                        lockFailed.incrementAndGet();
                        return;
                    }

                    try {
                        // 2. 락 받은 후 DB 작업
                        try (Connection conn = ds.getConnection()) {
                            conn.setAutoCommit(false);
                            boolean ok = booking.bookSeatRaw(conn, SEAT_ID, USER_ID, PRICE);
                            conn.commit();
                            if (ok) success.incrementAndGet();
                        }
                    } catch (SQLException e) {
                        // DB 실패
                    } finally {
                        // 3. Redis unlock
                        redis.eval(UNLOCK_LUA, ScriptOutputType.INTEGER,
                            new String[]{lockKey}, lockValue);
                    }
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
            // 락실패가 많더라도 최소 1번은 성공했어야 함 (단, 모든 시도가 락실패면 misses=1)
            if (success.get() != 1 && lockFailed.get() < ATTEMPTS) {
                misses = 1;
            }
        }

        return new RunResult(misses, lockFailed.get(), millis);
    }

    private record RunResult(int misses, int lockFailed, double millis) {}
}
