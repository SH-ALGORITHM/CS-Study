package stage;

import domain.StockTrade;
import infra.DataSourceFactory;
import infra.MeasurementLog;
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
 * STAGE 2-2. 낙관적 락 실습.
 *
 * StockTrade의 buyOptimistic 메서드를 여러 스레드가 동시에 호출한다.
 * FOR UPDATE로 기다리지 않고 version 조건이 맞을 때만 UPDATE한다.
 * version이 맞지 않으면 다른 트랜잭션이 먼저 수정한 것으로 보고 rollback 후 재시도한다.
 */
public class Stage2Optimistic {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 200;
    private static final int RUNS = 5;

    // 낙관락은 충돌 시 재시도하므로, 한 작업이 포기하기 전까지 허용할 최대 재시도 횟수.
    private static final int MAX_RETRIES = 10;

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
                "%nSTAGE 2-2 optimistic: avg misses=%.1f / avg failed=%.1f / avg %.1f ms%n",
                averageMisses,
                averageFailed,
                averageMillis
            );

            // failed에는 낙관락에서 maxRetries를 넘겨 처리되지 못한 작업 수를 기록한다.
            MeasurementLog.save("s2-2", "optimistic version", averageMisses, averageFailed, averageMillis);
        } finally {
            DataSourceFactory.close(dataSource);
        }
    }

    private static RunResult runOnce(DataSource dataSource, StockTrade stockTrade) throws Exception {
        SchemaBootstrap.resetStockTrade(dataSource);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        // 모든 작업을 같은 시점에 출발시켜 version 충돌이 발생하도록 만든다.
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        for (int i = 0; i < ATTEMPTS; i++) {
            executor.submit(() -> {
                try {
                    start.await();

                    // 내부에서 SELECT로 읽은 version과 UPDATE 시점의 version을 비교한다.
                    // 충돌이 나면 rollback 후 MAX_RETRIES 안에서 처음부터 재시도한다.
                    boolean ok = stockTrade.buyOptimistic(USER_ID, TICKER, QTY, PRICE, MAX_RETRIES);
                    if (ok) {
                        success.incrementAndGet();
                    } else {
                        // false는 잔고 부족 또는 maxRetries 초과를 의미한다.
                        // 현재 시나리오에서는 잔고가 충분하므로 대부분 maxRetries 초과로 해석한다.
                        failed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
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

        // 낙관락은 충돌이 심하면 일부 작업이 maxRetries를 초과해 실패할 수 있다.
        // 따라서 ATTEMPTS 전체가 아니라 실제 성공한 횟수만큼 DB에 반영됐는지 검증한다.
        int expectedSuccess = success.get();

        // 성공한 매수 1건마다 cash는 QTY * PRICE 만큼 줄고, holding은 QTY 만큼 늘어야 한다.
        BigDecimal expectedCash = INITIAL_CASH.subtract(
            PRICE.multiply(BigDecimal.valueOf(QTY)).multiply(BigDecimal.valueOf(expectedSuccess))
        );
        long expectedQty = INITIAL_QTY + (QTY * expectedSuccess);

        // success + failed가 전체 시도 수와 다르면 작업 집계 자체가 누락된 것이다.
        // cash/qty가 성공 횟수 기준 기대값과 다르면 lost update 또는 rollback 누락 가능성이 있다.
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
