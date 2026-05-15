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

import domain.BankAccount;
import infra.DataSourceFactory;
import infra.MeasurementLog;
import infra.RedisClientFactory;
import infra.SchemaBootstrap;

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
 * - 실패: 락 획득 실패 횟수 (다른 트랜잭션이 보유 중)
 *
 * <h3>fail-fast trade-off</h3>
 * 공정성 없음. 높은 contention 에서 starvation 가능.
 * 실무 개선: exponential backoff 또는 Redisson Pub/Sub.
 */
public class Stage2Distributed {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 200;
    private static final int RUNS = 5;
    private static final long FROM_ID = 1L;
    private static final long TO_ID = 2L;
    private static final BigDecimal AMOUNT = new BigDecimal("10");

    private static final String UNLOCK_LUA = """
        if redis.call('get', KEYS[1]) == ARGV[1] then
          return redis.call('del', KEYS[1])
        else
          return 0
        end
        """;

    public static void main(String[] args) throws Exception {
        DataSource ds = DataSourceFactory.create(THREADS);
        BankAccount bank = new BankAccount();

        System.out.println("[워밍업] 1 회");
        runOnce(ds, bank);

        double totalMisses = 0, totalLockFailed = 0, totalMillis = 0;
        for (int run = 0; run < RUNS; run++) {
            RunResult r = runOnce(ds, bank);
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

    private static RunResult runOnce(DataSource ds, BankAccount bank) throws Exception {
        SchemaBootstrap.resetAccounts(ds);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger lockFailed = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);

        // 두 계좌 모두 보호하는 락 — id 작은 쪽 기준
        String lockKey = "lock:account:" + Math.min(FROM_ID, TO_ID);

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
                        // ⚠️ 핵심: DB commit 이 Redis unlock 보다 먼저.
                        //    commit 전 unlock 하면 다른 스레드가 미커밋 데이터 보고 작업 → 동시성 깨짐.
                        try (Connection conn = ds.getConnection()) {
                            conn.setAutoCommit(false);
                            bank.transferRaw(conn, FROM_ID, TO_ID, AMOUNT);
                            conn.commit();                          // ★ DB commit
                            conn.setAutoCommit(true);
                        }
                    } catch (SQLException e) {
                        // DB 실패 — fail-fast
                    } finally {
                        // 3. ★ Redis unlock — 본인이 잡은 락만 (Lua script 로 get + del 원자화)
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

        BigDecimal sum;
        try (Connection conn = ds.getConnection()) {
            sum = bank.getBalance(conn, FROM_ID).add(bank.getBalance(conn, TO_ID));
        }
        int misses = sum.compareTo(new BigDecimal("20000")) == 0 ? 0 : 1;

        return new RunResult(misses, lockFailed.get(), millis);
    }

    private record RunResult(int misses, int lockFailed, double millis) {}
}
