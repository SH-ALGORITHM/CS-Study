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
 * STAGE 2-3. 분산락 (Redis SETNX + TTL + Lua script) — P2P 송금.
 *
 * <h3>핵심 패턴</h3>
 * 1. {@code SET key value NX EX 5} — 원자적 잠금 + 5초 TTL
 * 2. fail-fast — 락 획득 실패 시 즉시 false (재시도 X)
 * 3. Lua script — get + del 원자화 (본인 락만 안전 해제)
 *
 * <h3>측정값 해석</h3>
 * - 누락: 락 잘 동작했으면 0 (원금 보존)
 * - 락실패: 락 획득 실패 횟수 (다른 트랜잭션 보유 중)
 *
 * <h3>실무 트레이드오프</h3>
 * fail-fast 는 공정성 없음 → 높은 contention 에서 락 못 잡는 트랜잭션 다수.
 * 실무 개선: exponential backoff 또는 Redisson Pub/Sub.
 */
public class Stage2Distributed {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 200;
    private static final int RUNS = 5;
    private static final long FROM_ID = 1L;
    private static final long TO_ID = 2L;
    private static final BigDecimal AMOUNT = new BigDecimal("10");
    private static final BigDecimal INITIAL_TOTAL = BigDecimal.valueOf(2_000_000);

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
        System.out.printf("=== STAGE 2-3 분산락 P2P 송금 — %d 스레드 × %d 시도 × %d 회 평균 ===%n",
            THREADS, ATTEMPTS, RUNS);
        System.out.printf("누락 %.1f / 락실패 %.1f / 응답 %.1f ms%n",
            avgMisses, avgLockFailed, avgMillis);

        MeasurementLog.save("s2-3", "분산락 Redis SETNX (P2P)", avgMisses, avgLockFailed, avgMillis);

        DataSourceFactory.close(ds);
        RedisClientFactory.shutdown();
    }

    private static RunResult runOnce(DataSource ds, P2PWallet wallet) throws Exception {
        SchemaBootstrap.reset(ds);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger lockFailed = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);

        // 두 사용자 모두 보호하는 락 — id 작은 쪽 기준
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

                    // 1. 잠금 시도 (NX EX 5초)
                    String result = redis.set(lockKey, lockValue, SetArgs.Builder.nx().ex(5));
                    if (!"OK".equals(result)) {
                        lockFailed.incrementAndGet();
                        return;
                    }

                    try {
                        // 2. 락 받은 후 DB 작업 (transferRaw — 락 X)
                        // ⚠️ DB commit 이 Redis unlock 보다 먼저.
                        try (Connection conn = ds.getConnection()) {
                            conn.setAutoCommit(false);
                            wallet.transferRaw(conn, FROM_ID, TO_ID, AMOUNT);
                            conn.commit();
                            conn.setAutoCommit(true);
                        }
                    } catch (SQLException e) {
                        // DB 실패 — fail-fast
                    } finally {
                        // 3. Redis unlock — Lua script 로 본인 락만 안전 해제
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
