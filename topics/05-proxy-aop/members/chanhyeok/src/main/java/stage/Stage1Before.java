package stage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
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

import domain.P2PWallet;
import infra.MeasurementLog;
import infra.SchemaBootstrap;

/**
 * STAGE 1 BEFORE — 3 주차 분산락 보일러플레이트 그대로 (비교 baseline).
 *
 * <h3>보일러플레이트의 정체</h3>
 * 송금 메서드 한 개 짜는데 들어가는 분산락 관련 코드:
 * <ol>
 *   <li>lockKey 문자열 조합</li>
 *   <li>lockValue UUID 생성</li>
 *   <li>RedisClient connect / try-with-resources</li>
 *   <li>SETNX EX 호출 + 결과 체크</li>
 *   <li>try-finally 로 Lua unlock 보장</li>
 *   <li>Lua script 문자열 또는 상수</li>
 * </ol>
 *
 * <p>이 6 가지가 락 필요한 모든 메서드마다 반복.
 * → STAGE 2 AFTER 에서 {@code @DistributedLock} 한 줄로 추출.
 */
public class Stage1Before {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 200;
    private static final long FROM_ID = 1L;
    private static final long TO_ID = 2L;
    private static final BigDecimal AMOUNT = new BigDecimal("10");
    private static final BigDecimal INITIAL_TOTAL = BigDecimal.valueOf(2_000_000);

    // ★ 보일러플레이트 #1 — Lua script 문자열
    private static final String UNLOCK_LUA = """
        if redis.call('get', KEYS[1]) == ARGV[1] then
          return redis.call('del', KEYS[1])
        else
          return 0
        end
        """;

    public static void main(String[] args) throws Exception {
        DataSource ds = createDataSource();
        RedisClient redisClient = RedisClient.create("redis://localhost:6379");
        P2PWallet wallet = new P2PWallet();

        SchemaBootstrap.reset(ds);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger lockFailed = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < ATTEMPTS; i++) {
            executor.submit(() -> {
                try { start.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                // ★ 보일러플레이트 #2~#6 — 메서드 본문 매번 반복
                transferWithBoilerplate(ds, redisClient, wallet, FROM_ID, TO_ID, AMOUNT, lockFailed);
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

        System.out.println();
        System.out.println("=== STAGE 1 BEFORE — 보일러플레이트 그대로 ===");
        System.out.printf("누락 %d / 락실패 %d / 응답 %.1f ms%n", misses, lockFailed.get(), millis);
        System.out.println("(transferWithBoilerplate 메서드 본문 라인 수 — 약 25 줄. 락 관련 코드만)");

        MeasurementLog.save("s1-before", "보일러플레이트", misses, lockFailed.get(), millis);

        ((HikariDataSource) ds).close();
        redisClient.shutdown();
    }

    /**
     * ★ 핵심 — 락 필요한 메서드마다 매번 반복되는 25 줄.
     *
     * <p>비즈니스 로직 (transferRaw 호출) 은 1 줄. 나머지 24 줄이 락 인프라.
     */
    private static void transferWithBoilerplate(DataSource ds, RedisClient redisClient,
            P2PWallet wallet, long fromId, long toId, BigDecimal amount, AtomicInteger lockFailed) {

        String lockKey = "lock:wallet:" + Math.min(fromId, toId);
        String lockValue = UUID.randomUUID().toString();

        try (StatefulRedisConnection<String, String> rconn = redisClient.connect()) {
            RedisCommands<String, String> redis = rconn.sync();

            // 1. SETNX + TTL
            String result = redis.set(lockKey, lockValue, SetArgs.Builder.nx().ex(5));
            if (!"OK".equals(result)) {
                lockFailed.incrementAndGet();
                return;
            }

            try {
                // 2. 진짜 비즈니스 로직 — 이 1 줄을 위해 위의 24 줄
                try (Connection conn = ds.getConnection()) {
                    conn.setAutoCommit(false);
                    wallet.transferRaw(conn, fromId, toId, amount);
                    conn.commit();
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                // fail-fast
            } finally {
                // 3. Lua unlock
                redis.eval(UNLOCK_LUA, ScriptOutputType.INTEGER,
                    new String[]{lockKey}, lockValue);
            }
        }
    }

    private static DataSource createDataSource() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:postgresql://localhost:5433/csstudy");
        cfg.setUsername("csstudy");
        cfg.setPassword("csstudy1234");
        cfg.setMaximumPoolSize(THREADS);
        return new HikariDataSource(cfg);
    }
}
