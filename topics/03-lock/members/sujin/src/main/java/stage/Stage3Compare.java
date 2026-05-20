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
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * STAGE 3. 충돌 빈도별 락 전략 비교.
 *
 * 같은 매수 요청 수를 1개/10개/100개 포트폴리오에 분산시켜
 * 충돌 빈도가 달라질 때 비관락, 낙관락, Redis 분산락의 결과를 비교한다.
 *
 * 누락(misses)은 실제 성공 횟수와 DB 최종 상태가 맞지 않는 경우를 의미한다.
 * 실패(failed)는 낙관락의 maxRetries 초과 또는 Redis lock 획득 실패처럼
 * 요청은 들어왔지만 거래가 완료되지 못한 횟수다.
 */
public class Stage3Compare {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 200;
    private static final int RUNS = 5;
    private static final int MAX_RETRIES = 10;

    private static final long QTY = 1L;
    private static final BigDecimal PRICE = new BigDecimal("1000");
    private static final BigDecimal INITIAL_CASH = new BigDecimal(SchemaBootstrap.INITIAL_CASH);
    private static final long INITIAL_QTY = SchemaBootstrap.INITIAL_QTY;

    public static void main(String[] args) throws Exception {
        DataSource dataSource = DataSourceFactory.create(THREADS);
        StockTrade stockTrade = new StockTrade(dataSource);

        try {
            System.out.println("[warmup]");
            runOnce(dataSource, stockTrade, LockMode.PESSIMISTIC, Contention.HIGH);

            System.out.printf(
                "%n=== STAGE 3 - contention comparison (%d threads x %d attempts x %d run average) ===%n",
                THREADS,
                ATTEMPTS,
                RUNS
            );
            System.out.println("| contention | lock | success | failed | misses | avg ms |");
            System.out.println("|---|---|---:|---:|---:|---:|");

            for (Contention contention : Contention.values()) {
                for (LockMode lockMode : LockMode.values()) {
                    AverageResult average = measure(dataSource, stockTrade, lockMode, contention);
                    printRow(contention, lockMode, average);
                    MeasurementLog.save(
                        "s3",
                        contention.label() + " / " + lockMode.label(),
                        average.misses(),
                        average.failed(),
                        average.millis()
                    );
                }
            }
        } finally {
            DataSourceFactory.close(dataSource);
            RedisClientFactory.shutdown();
        }
    }

    // 같은 조건을 RUNS번 반복해 1회성 튐 값을 줄이고 평균값으로 비교한다.
    private static AverageResult measure(
        DataSource dataSource,
        StockTrade stockTrade,
        LockMode lockMode,
        Contention contention
    ) throws Exception {
        double totalSuccess = 0;
        double totalFailed = 0;
        double totalMisses = 0;
        double totalMillis = 0;

        for (int run = 1; run <= RUNS; run++) {
            RunResult result = runOnce(dataSource, stockTrade, lockMode, contention);
            System.out.printf(
                "[%s / %s] RUN %d: success=%d / failed=%d / misses=%d / %.1f ms%n",
                contention.label(),
                lockMode.label(),
                run,
                result.success(),
                result.failed(),
                result.misses(),
                result.millis()
            );

            totalSuccess += result.success();
            totalFailed += result.failed();
            totalMisses += result.misses();
            totalMillis += result.millis();
        }

        return new AverageResult(
            totalSuccess / RUNS,
            totalFailed / RUNS,
            totalMisses / RUNS,
            totalMillis / RUNS
        );
    }

    // 충돌 빈도 조건에 맞춰 실험 데이터를 매번 초기화한다.
    // portfolioCount가 작을수록 같은 row에 요청이 몰려 충돌이 커진다.
    private static RunResult runOnce(
        DataSource dataSource,
        StockTrade stockTrade,
        LockMode lockMode,
        Contention contention
    ) throws Exception {
        SchemaBootstrap.resetStockTrade(dataSource, contention.portfolioCount());

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicIntegerArray successByPortfolio = new AtomicIntegerArray(contention.portfolioCount());

        // i % portfolioCount로 요청을 분산한다.
        // high는 모든 요청이 0번 포트폴리오로, low는 100개 포트폴리오로 퍼진다.
        for (int i = 0; i < ATTEMPTS; i++) {
            int portfolioIndex = i % contention.portfolioCount();
            executor.submit(() -> {
                try {
                    start.await();

                    long userId = SchemaBootstrap.USER_ID + portfolioIndex;
                    String ticker = SchemaBootstrap.tickerOf(portfolioIndex);
                    boolean ok = buy(stockTrade, lockMode, userId, ticker);

                    if (ok) {
                        success.incrementAndGet();
                        successByPortfolio.incrementAndGet(portfolioIndex);
                    } else {
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

        int misses = verify(dataSource, contention, success.get(), failed.get(), successByPortfolio);
        return new RunResult(success.get(), failed.get(), misses, millis);
    }

    // 선택한 락 전략에 따라 같은 매수 요청을 서로 다른 방식으로 처리한다.
    private static boolean buy(StockTrade stockTrade, LockMode lockMode, long userId, String ticker)
        throws SQLException {
        return switch (lockMode) {
            case PESSIMISTIC -> stockTrade.buyPessimistic(userId, ticker, QTY, PRICE);
            case OPTIMISTIC -> stockTrade.buyOptimistic(userId, ticker, QTY, PRICE, MAX_RETRIES);
            case DISTRIBUTED -> stockTrade.buyWithDistributedLock(userId, ticker, QTY, PRICE);
        };
    }

    // 각 포트폴리오별 성공 횟수를 기준으로 기대 cash/qty를 계산한다.
    // 최종 DB 상태가 기대값과 다르면 lost update 또는 rollback 누락 가능성이 있다.
    private static int verify(
        DataSource dataSource,
        Contention contention,
        int success,
        int failed,
        AtomicIntegerArray successByPortfolio
    ) throws SQLException {
        int misses = success + failed == ATTEMPTS ? 0 : 1;

        try (Connection connection = dataSource.getConnection()) {
            for (int index = 0; index < contention.portfolioCount(); index++) {
                long userId = SchemaBootstrap.USER_ID + index;
                String ticker = SchemaBootstrap.tickerOf(index);
                Portfolio portfolio = readPortfolio(connection, userId, ticker);
                int expectedSuccess = successByPortfolio.get(index);

                BigDecimal expectedCash = INITIAL_CASH.subtract(
                    PRICE.multiply(BigDecimal.valueOf(QTY)).multiply(BigDecimal.valueOf(expectedSuccess))
                );
                long expectedQty = INITIAL_QTY + (QTY * expectedSuccess);

                if (portfolio.cash().compareTo(expectedCash) != 0) {
                    misses++;
                }
                if (portfolio.qty() != expectedQty) {
                    misses++;
                }
            }
        }

        return misses;
    }

    private static Portfolio readPortfolio(Connection connection, long userId, String ticker) throws SQLException {
        BigDecimal cash;
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT cash
            FROM wallet
            WHERE user_id = ?
            """)) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("wallet not found: userId=" + userId);
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
            statement.setLong(1, userId);
            statement.setString(2, ticker);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("holding not found: userId=" + userId + ", ticker=" + ticker);
                }
                qty = resultSet.getLong("qty");
            }
        }

        return new Portfolio(cash, qty);
    }

    private static void printRow(Contention contention, LockMode lockMode, AverageResult result) {
        System.out.printf(
            "| %s | %s | %.1f | %.1f | %.1f | %.1f |%n",
            contention.label(),
            lockMode.label(),
            result.success(),
            result.failed(),
            result.misses(),
            result.millis()
        );
    }

    // row 분산 개수로 충돌 빈도를 표현한다.
    // 100개 row는 낮은 충돌, 10개 row는 중간 충돌, 1개 row는 높은 충돌이다.
    private enum Contention {
        LOW("low - 100 rows", 100),
        MEDIUM("medium - 10 rows", 10),
        HIGH("high - 1 row", 1);

        private final String label;
        private final int portfolioCount;

        Contention(String label, int portfolioCount) {
            this.label = label;
            this.portfolioCount = portfolioCount;
        }

        private String label() {
            return label;
        }

        private int portfolioCount() {
            return portfolioCount;
        }
    }

    // STAGE 3에서 비교할 세 가지 락 전략.
    private enum LockMode {
        PESSIMISTIC("pessimistic FOR UPDATE"),
        OPTIMISTIC("optimistic version"),
        DISTRIBUTED("redis SET NX EX");

        private final String label;

        LockMode(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }
    }

    private record RunResult(int success, int failed, int misses, double millis) {
    }

    private record AverageResult(double success, double failed, double misses, double millis) {
    }

    private record Portfolio(BigDecimal cash, long qty) {
    }
}
