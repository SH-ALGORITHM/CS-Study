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
 * STAGE 2-1. 비관적 락 실습.
 *
 * StockTrade의 buyPessimistic 메서드를 여러 스레드가 동시에 호출한다.
 * 내부에서는 항상 wallet -> holding 순서로 SELECT FOR UPDATE를 수행하므로
 * 데드락 없이 모든 매수를 직렬 처리하는지 확인한다.
 */
public class Stage2Pessimistic {

    // 커넥션 풀 대기가 측정에 섞이지 않도록 THREADS와 pool size를 맞춘다.
    private static final int THREADS = 50;

    // 같은 row(wallet, holding)에 동시에 접근시켜 lock wait가 실제로 발생하도록 충분한 시도 수를 둔다.
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
            // 첫 실행은 커넥션 풀 초기화, JIT, DB 캐시 영향이 섞이므로 측정 평균에서 제외한다.
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
                "%nSTAGE 2-1 pessimistic: avg misses=%.1f / avg failed=%.1f / avg %.1f ms%n",
                averageMisses,
                averageFailed,
                averageMillis
            );

            MeasurementLog.save("s2-1", "pessimistic FOR UPDATE", averageMisses, averageFailed, averageMillis);
        } finally {
            DataSourceFactory.close(dataSource);
        }
    }

    private static RunResult runOnce(DataSource dataSource, StockTrade stockTrade) throws Exception {
        // 매 측정은 같은 cash/qty/version에서 시작해야 결과를 비교할 수 있다. (매번 초기화)
        SchemaBootstrap.resetStockTrade(dataSource);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        // 모든 작업을 미리 executor에 올려둔 뒤 동시에 출발시켜 경합을 만든다.
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        for (int i = 0; i < ATTEMPTS; i++) {
            executor.submit(() -> {
                try {
                    // start.countDown() 전까지 모든 스레드를 출발선에 세운다.
                    start.await();

                    // 내부에서 wallet -> holding 순서로 FOR UPDATE를 걸어 데드락을 피한다.
                    boolean ok = stockTrade.buyPessimistic(USER_ID, TICKER, QTY, PRICE);
                    if (ok) {
                        success.incrementAndGet();
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

        // submit 직후 바로 출발시키면 아직 대기점에 도달하지 못한 작업이 있을 수 있어 짧게 정렬 시간을 둔다.
        Thread.sleep(50);
        long startedAt = System.nanoTime();
        start.countDown();
        executor.shutdown();

        // lock wait가 길어질 수 있으므로 timeout은 넉넉히 둔다.
        boolean finished = executor.awaitTermination(300, TimeUnit.SECONDS);
        double millis = (System.nanoTime() - startedAt) / 1_000_000.0;

        if (!finished) {
            executor.shutdownNow();
            throw new IllegalStateException("executor timeout");
        }

        Portfolio portfolio = readPortfolio(dataSource);
        int expectedSuccess = ATTEMPTS;
        // 모든 매수가 성공했다면 현금은 성공 횟수 * QTY * PRICE 만큼 줄고, 보유 수량은 성공 횟수 * QTY 만큼 늘어야 한다.
        BigDecimal expectedCash = INITIAL_CASH.subtract(
            PRICE.multiply(BigDecimal.valueOf(QTY)).multiply(BigDecimal.valueOf(expectedSuccess))
        );
        long expectedQty = INITIAL_QTY + (QTY * expectedSuccess);

        // 비관적 락에서는 성공 횟수와 최종 DB 상태가 기대값과 일치해야 한다.
        int misses = 0;
        if (success.get() != expectedSuccess) {
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

    // 측정 종료 후 DB의 최종 상태를 직접 조회해 lost update가 없었는지 검증한다.
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
