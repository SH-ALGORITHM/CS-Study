package stage;

import domain.PointCheckout;
import infra.DataSourceFactory;
import infra.MeasurementLog;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CheckoutRaceS2 {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 200;
    private static final int INITIAL_STOCK = 200;
    private static final int INITIAL_POINT = 10_000;
    private static final int PRICE = 1_000;
    private static final long ITEM_ID = 1L;
    private static final long ORDER_ID_BASE = 10_000L;
    private static final long USER_ID_BASE = 1_000L;

    public static void main(String[] args) throws Exception {
        DataSource ds = DataSourceFactory.create(THREADS);
        resetSchema(ds);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < ATTEMPTS; i++) {
            long orderId = ORDER_ID_BASE + i;
            long userId = USER_ID_BASE + i;
            PointCheckout checkout = new PointCheckout(orderId, ITEM_ID, userId, PRICE);
            executor.submit(checkoutTask(ds, checkout, start, success, failed));
        }

        Thread.sleep(50);
        long t0 = System.nanoTime();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        double millis = (System.nanoTime() - t0) / 1_000_000.0;

        int actualStock = findStock(ds);
        int expectedStock = INITIAL_STOCK - success.get();
        int lostUpdates = actualStock - expectedStock;
        int orderCount = countOrders(ds);

        System.out.println("=== STAGE 2-1: 장바구니 결제 race 재현 (READ_COMMITTED + RMW) ===");
        System.out.printf("시도: %d, 성공: %d, 실패: %d%n", ATTEMPTS, success.get(), failed.get());
        System.out.printf("생성된 주문: %d%n", orderCount);
        System.out.printf("최종 재고: %d (기대값: %d)%n", actualStock, expectedStock);
        System.out.printf("Lost Update: %d%n", lostUpdates);
        System.out.printf("응답시간: %.1fms%n", millis);

        MeasurementLog.save("s2-1", "checkout READ_COMMITTED", lostUpdates, failed.get(), millis);
        DataSourceFactory.close(ds);
    }

    private static Callable<Void> checkoutTask(
        DataSource ds,
        PointCheckout checkout,
        CountDownLatch start,
        AtomicInteger success,
        AtomicInteger failed
    ) {
        return () -> {
            Connection conn = null;
            try {
                start.await();
                conn = ds.getConnection();
                conn.setAutoCommit(false);
                conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                boolean ok = checkout.checkout(conn);
                conn.commit();

                if (ok) {
                    success.incrementAndGet();
                }
            } catch (Exception e) {
                failed.incrementAndGet();
                rollbackQuietly(conn);
            } finally {
                if (conn != null) {
                    conn.close();
                }
            }
            return null;
        };
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
            for (int i = 0; i < ATTEMPTS; i++) {
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

    private static int countOrders(DataSource ds) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM orders");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void rollbackQuietly(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.rollback();
        } catch (SQLException ignored) {
        }
    }
}
