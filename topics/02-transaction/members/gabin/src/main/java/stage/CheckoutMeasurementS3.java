package stage;

import domain.PointCheckout;
import infra.DataSourceFactory;
import infra.MeasurementLog;
import infra.TransactionHelper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CheckoutMeasurementS3 {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 200;
    private static final int RUNS = 5;
    private static final int INITIAL_STOCK = 200;
    private static final int INITIAL_POINT = 10_000;
    private static final int PRICE = 1_000;
    private static final long ITEM_ID = 1L;
    private static final long ORDER_ID_BASE = 30_000L;
    private static final long USER_ID_BASE = 3_000L;

    public static void main(String[] args) throws Exception {
        DataSource ds = DataSourceFactory.create(THREADS);

        Result rc = measure(ds, Connection.TRANSACTION_READ_COMMITTED, "READ_COMMITTED");
        Result rr = measure(ds, Connection.TRANSACTION_REPEATABLE_READ, "REPEATABLE_READ");
        Result sr = measure(ds, Connection.TRANSACTION_SERIALIZABLE, "SERIALIZABLE");

        System.out.println();
        System.out.println("| 격리 수준 | Lost Update 평균 | 실패 평균 | 응답시간 평균(ms) |");
        System.out.println("|---|---:|---:|---:|");
        printRow("READ_COMMITTED", rc);
        printRow("REPEATABLE_READ", rr);
        printRow("SERIALIZABLE", sr);

        MeasurementLog.save("s3", "READ_COMMITTED", rc.avgLostUpdates(), rc.avgFailed(), rc.avgMillis());
        MeasurementLog.save("s3", "REPEATABLE_READ", rr.avgLostUpdates(), rr.avgFailed(), rr.avgMillis());
        MeasurementLog.save("s3", "SERIALIZABLE", sr.avgLostUpdates(), sr.avgFailed(), sr.avgMillis());

        DataSourceFactory.close(ds);
    }

    private static Result measure(DataSource ds, int isolationLevel, String name) throws Exception {
        double totalLostUpdates = 0;
        double totalFailed = 0;
        double totalMillis = 0;

        for (int run = 0; run < RUNS; run++) {
            RunResult result = runOnce(ds, isolationLevel, run);
            totalLostUpdates += result.lostUpdates();
            totalFailed += result.failed();
            totalMillis += result.millis();
        }

        Result avg = new Result(
            totalLostUpdates / RUNS,
            totalFailed / RUNS,
            totalMillis / RUNS
        );

        System.out.printf("%s 측정 완료: Lost Update %.1f, 실패 %.1f, 응답시간 %.1fms%n",
            name, avg.avgLostUpdates(), avg.avgFailed(), avg.avgMillis());
        return avg;
    }

    private static RunResult runOnce(DataSource ds, int isolationLevel, int run) throws Exception {
        resetSchema(ds);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);

        long orderBase = ORDER_ID_BASE + ((long) run * ATTEMPTS);
        long userBase = USER_ID_BASE + ((long) run * ATTEMPTS);

        for (int i = 0; i < ATTEMPTS; i++) {
            long orderId = orderBase + i;
            long userId = userBase + i;
            PointCheckout checkout = new PointCheckout(orderId, ITEM_ID, userId, PRICE);

            executor.submit(() -> {
                try {
                    start.await();
                    boolean ok = TransactionHelper.execute(ds, isolationLevel, checkout::checkout);
                    if (ok) {
                        success.incrementAndGet();
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                }
            });
        }

        Thread.sleep(50);
        long t0 = System.nanoTime();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        double millis = (System.nanoTime() - t0) / 1_000_000.0;

        int actualStock = findStock(ds);
        int expectedStock = INITIAL_STOCK - success.get();
        int lostUpdates = Math.max(0, actualStock - expectedStock);

        return new RunResult(lostUpdates, failed.get(), millis);
    }

    private static void resetSchema(DataSource ds) throws SQLException {
        try (Connection conn = ds.getConnection()) {
            execute(conn, """
                CREATE TABLE IF NOT EXISTS stock (
                    item_id BIGINT PRIMARY KEY,
                    quantity INT NOT NULL
                )
                """);
            execute(conn, """
                CREATE TABLE IF NOT EXISTS user_point (
                    user_id BIGINT PRIMARY KEY,
                    balance INT NOT NULL
                )
                """);
            execute(conn, """
                CREATE TABLE IF NOT EXISTS orders (
                    order_id BIGINT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    item_id BIGINT NOT NULL,
                    paid_point INT NOT NULL,
                    status VARCHAR(20) NOT NULL
                )
                """);
            execute(conn, "TRUNCATE TABLE orders, stock, user_point");
            execute(conn, "INSERT INTO stock (item_id, quantity) VALUES (1, 200)");
            insertUserPoints(conn);
        }
    }

    private static void insertUserPoints(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO user_point (user_id, balance) VALUES (?, ?)")) {
            for (int i = 0; i < RUNS * ATTEMPTS; i++) {
                ps.setLong(1, USER_ID_BASE + i);
                ps.setInt(2, INITIAL_POINT);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void execute(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        }
    }

    private static int findStock(DataSource ds) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT quantity FROM stock WHERE item_id = 1");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void printRow(String isolationLevel, Result result) {
        System.out.printf("| %s | %.1f | %.1f | %.1f |%n",
            isolationLevel,
            result.avgLostUpdates(),
            result.avgFailed(),
            result.avgMillis());
    }

    private record RunResult(int lostUpdates, int failed, double millis) {
    }

    private record Result(double avgLostUpdates, double avgFailed, double avgMillis) {
    }
}
