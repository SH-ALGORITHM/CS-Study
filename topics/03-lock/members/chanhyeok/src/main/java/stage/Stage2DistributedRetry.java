package stage;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import domain.P2PWallet;
import infra.DataSourceFactory;
import infra.MeasurementLog;
import infra.RedisClientFactory;
import infra.SchemaBootstrap;

/**
 * STAGE 2-3 (변형). 분산락 — 락 획득 재시도 추가 (fail-fast 보강).
 *
 * <h3>차이점</h3>
 * Stage2Distributed (fail-fast) vs Stage2DistributedRetry (재시도):
 * - fail-fast: SET NX 실패 → 즉시 종료. 처리 성공률 1.5%
 * - retry: SET NX 실패 → 10 ms 대기 후 재시도, 50 회 한도. 처리 성공률 거의 100%
 *
 * <h3>실무 비교</h3>
 * Redisson 의 watchdog + Pub/Sub 패턴의 단순화. 실제 Redisson 은 락 풀릴 때 알림 받지만,
 * 여기서는 단순 sleep + retry 로 흉내.
 *
 * <h3>예상 측정 변화</h3>
 * - lockFailed: 197 → 거의 0
 * - 처리 성공률: 1.5% → 거의 100%
 * - 응답시간: 104 ms → 더 길어짐 (대기 + 작업 누적)
 */
public class Stage2DistributedRetry {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 200;
    private static final int RUNS = 5;
    private static final long FROM_ID = 1L;
    private static final long TO_ID = 2L;
    private static final BigDecimal AMOUNT = new BigDecimal("10");
    private static final BigDecimal INITIAL_TOTAL = BigDecimal.valueOf(2_000_000);

    // 락 획득 재시도 — fail-fast 보강
    private static final int MAX_LOCK_ATTEMPTS = 50;
    private static final long BACKOFF_MS = 10;

    private static final String UNLOCK_LUA = """
        if redis.call('get', KEYS[1]) == ARGV[1] then
          return redis.call('del', KEYS[1])
        else
          return 0
        end
        """;

    public static void main(String[] args) throws Exception {
        DataSource ds = DataSourceFactory.create(THREADS);
        P2PWallet wallet = new P2PWallet();

        System.out.println("[워밍업] 1 회");
        runOnce(ds, wallet);

        double totalMisses = 0, totalLockFailed = 0, totalMillis = 0;
        for (int run = 0; run < RUNS; run++) {
            RunResult r = runOnce(ds, wallet);
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
        System.out.printf("=== STAGE 2-3 분산락 재시도 P2P 송금 — %d 스레드 × %d 시도 × %d 회 평균 ===%n",
            THREADS, ATTEMPTS, RUNS);
        System.out.printf("누락 %.1f / 락실패 %.1f / 응답 %.1f ms%n",
            avgMisses, avgLockFailed, avgMillis);

        MeasurementLog.save("s2-3r", "분산락 retry (P2P)", avgMisses, avgLockFailed, avgMillis);

        DataSourceFactory.close(ds);
        RedisClientFactory.shutdown();
    }

    private static RunResult runOnce(DataSource ds, P2PWallet wallet) throws Exception {
        SchemaBootstrap.reset(ds);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger lockFailed = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);

        String lockKey = "lock:wallet:" + Math.min(FROM_ID, TO_ID);

        for (int i = 0; i < ATTEMPTS; i++) {
            executor.submit(() -> {
                try { start.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                String lockValue = UUID.randomUUID().toString();

                try (StatefulRedisConnection<String, String> rconn = RedisClientFactory.connect()) {
                    RedisCommands<String, String> redis = rconn.sync();

                    // ★ 락 획득 재시도 — fail-fast 보강
                    boolean locked = false;
                    for (int attempt = 0; attempt < MAX_LOCK_ATTEMPTS; attempt++) {
                        String result = redis.set(lockKey, lockValue, SetArgs.Builder.nx().ex(5));
                        if ("OK".equals(result)) {
                            locked = true;
                            break;
                        }
                        try {
                            Thread.sleep(BACKOFF_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    if (!locked) {
                        lockFailed.incrementAndGet();
                        return;
                    }

                    try {
                        try (Connection conn = ds.getConnection()) {
                            conn.setAutoCommit(false);
                            wallet.transferRaw(conn, FROM_ID, TO_ID, AMOUNT);
                            conn.commit();
                            conn.setAutoCommit(true);
                        }
                    } catch (SQLException e) {
                        // DB 실패 — fail-fast
                    } finally {
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

        BigDecimal total;
        try (Connection conn = ds.getConnection()) {
            total = wallet.balanceOf(conn, FROM_ID)
                .add(wallet.balanceOf(conn, TO_ID))
                .add(wallet.feeTotal(conn));
        }
        int misses = total.compareTo(INITIAL_TOTAL) == 0 ? 0 : 1;

        return new RunResult(misses, lockFailed.get(), millis);
    }

    private record RunResult(int misses, int lockFailed, double millis) {}
}
