package stage;

import domain.StockTrade;
import infra.DataSourceFactory;
import infra.MeasurementLog;
import infra.RedisClientFactory;
import infra.SchemaBootstrap;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * STAGE 2-3. Redis 분산락 실습.
 *
 * lock:ticker:{ticker} 키를 잡은 작업만 DB 거래를 수행한다.
 * Redis lock 획득 실패는 fail-fast로 처리되어 failed에 기록된다.
 */
/**
 * STAGE 2-3. Redis 분산락 실습.
 *
 * ticker 단위 Redis lock을 먼저 획득한 작업만 DB 거래를 수행한다.
 * 같은 종목에 대한 외부 거래소 API 호출을 여러 서버가 동시에 보내지 않도록
 * 직렬화하는 상황을 흉내낸다.
 *
 * 현재 구현은 fail-fast 전략이다.
 * Redis lock 획득에 실패한 작업은 기다리지 않고 false를 반환하며,
 * 해당 횟수를 failed에 기록한다.
 */
public class Stage2Distributed {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 200;
    private static final int RUNS = 5;

    private static final long USER_ID = SchemaBootstrap.USER_ID;
    private static final String TICKER = SchemaBootstrap.TICKER;
    private static final long QTY = 1L;
    private static final BigDecimal PRICE = new BigDecimal("1000");

    private static final BigDecimal INITIAL_CASH = new BigDecimal("1000000");
    private static final long INITIAL_QTY = 10L;

    public static void main(String[] args) throws Exception {
        DataSource dataSource = DataSourceFactory.create(THREADS);
        StockTrade stockTrade = new StockTrade(dataSource);

        try {
            System.out.println("[warmup]");
            runOnce(dataSource, stockTrade);

            double totalMisses = 0;
            double totalFailed = 0;
            double totalMillis = 0;

            for (int run = 1; run <= RUNS; run++) {
                RunResult result = runOnce(dataSource, stockTrade);
                System.out.printf(
                    "RUN %d: success=%d / failed=%d / misses=%d / %.1f ms%n",
                    run,
                    result.success(),
                    result.failed(),
                    result.misses(),
                    result.millis()
                );
                totalMisses += result.misses();
                totalFailed += result.failed();
                totalMillis += result.millis();
            }

            double averageMisses = totalMisses / RUNS;
            double averageFailed = totalFailed / RUNS;
            double averageMillis = totalMillis / RUNS;

            System.out.printf(
                "%nSTAGE 2-3 distributed: avg misses=%.1f / avg failed=%.1f / avg %.1f ms%n",
                averageMisses,
                averageFailed,
                averageMillis
            );

            // failed에는 Redis lock 획득 실패 또는 SQL 예외로 처리되지 못한 작업 수를 기록한다.
            MeasurementLog.save("s2-3", "redis SET NX EX", averageMisses, averageFailed, averageMillis);
        } finally {
            DataSourceFactory.close(dataSource);
            RedisClientFactory.shutdown();
        }
    }

    private static RunResult runOnce(DataSource dataSource, StockTrade stockTrade) throws Exception {
        SchemaBootstrap.resetStockTrade(dataSource);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        for (int i = 0; i < ATTEMPTS; i++) {
            executor.submit(() -> {
                try {
                    start.await();

                    // Redis lock:ticker:{ticker} 획득에 성공한 작업만 내부 DB 거래를 수행한다.
                    // 획득 실패는 fail-fast로 false를 반환한다.
                    boolean ok = stockTrade.buyWithDistributedLock(USER_ID, TICKER, QTY, PRICE);
                    if (ok) {
                        success.incrementAndGet();
                    } else {
                        failed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // 현재 시나리오에서는 대부분 Redis lock 획득 실패를 의미한다.
                    // 기다리지 않는 전략이므로 높은 경합에서 실패 수가 늘어날 수 있다.
                    failed.incrementAndGet();
                } catch (SQLException e) {
                    failed.incrementAndGet();
                }
            });
        }

        Thread.sleep(50);
        long startedAt = System.nanoTime();
        start.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(300, TimeUnit.SECONDS);
        double millis = (System.nanoTime() - startedAt) / 1_000_000.0;

        if (!finished) {
            executor.shutdownNow();
            throw new IllegalStateException("executor timeout");
        }

        Portfolio portfolio = readPortfolio(dataSource);

        // Redis 분산락은 현재 fail-fast 전략이라 lock 획득 실패가 정상 관찰값이다.
        // 따라서 실제 성공한 횟수만큼만 DB에 반영됐는지 검증한다.
        int expectedSuccess = success.get();

        // 성공한 매수 1건마다 cash는 QTY * PRICE 만큼 줄고, holding은 QTY 만큼 늘어야 한다.
        BigDecimal expectedCash = INITIAL_CASH.subtract(
            PRICE.multiply(BigDecimal.valueOf(QTY)).multiply(BigDecimal.valueOf(expectedSuccess))
        );
        long expectedQty = INITIAL_QTY + (QTY * expectedSuccess);

        int misses = 0;
        if (success.get() + failed.get() != ATTEMPTS) {
            misses++;
        }
        if (portfolio.cash().compareTo(expectedCash) != 0) {
            misses++;
        }
        if (portfolio.qty() != expectedQty) {
            misses++;
        }

        System.out.printf(
            "final: cash=%s / qty=%d / expected cash=%s / expected qty=%d%n",
            portfolio.cash(),
            portfolio.qty(),
            expectedCash,
            expectedQty
        );

        return new RunResult(success.get(), failed.get(), misses, millis);
    }

    private static Portfolio readPortfolio(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            BigDecimal cash;
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT cash
                FROM wallet
                WHERE user_id = ?
                """)) {
                statement.setLong(1, USER_ID);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new SQLException("wallet not found: userId=" + USER_ID);
                    }
                    cash = resultSet.getBigDecimal("cash");
                }
            }

            long qty;
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT qty
                FROM holding
                WHERE user_id = ? AND ticker = ?
                """)) {
                statement.setLong(1, USER_ID);
                statement.setString(2, TICKER);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new SQLException("holding not found: userId=" + USER_ID + ", ticker=" + TICKER);
                    }
                    qty = resultSet.getLong("qty");
                }
            }

            return new Portfolio(cash, qty);
        }
    }

    private record RunResult(int success, int failed, int misses, double millis) {
    }

    private record Portfolio(BigDecimal cash, long qty) {
    }
}
