package stage;

import domain.PointCheckout;
import infra.DataSourceFactory;
import infra.MeasurementLog;
import infra.SchemaBootstrap;
import infra.TransactionHelper;

import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

public class CheckoutOptimisticS2 {

    private static final int THREADS = 2;
    private static final int MAX_RETRIES = 3;
    private static final long ITEM_ID = 1L;
    private static final int INITIAL_STOCK = 1;

    public static void main(String[] args) throws Exception {
        DataSource ds = DataSourceFactory.create(THREADS);
        PointCheckout checkout = new PointCheckout();

        SchemaBootstrap.resetCheckout(ds);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        for (long userId = 1; userId <= THREADS; userId++) {
            long currentUserId = userId;
            executor.submit(() -> {
                try {
                    start.await();
                    boolean ok = TransactionHelper.execute(
                        ds,
                        Connection.TRANSACTION_READ_COMMITTED,
                        conn -> checkout.checkoutOptimistic(conn, currentUserId, ITEM_ID, MAX_RETRIES)
                    );
                    if (ok) {
                        success.incrementAndGet();
                    } else {
                        failed.incrementAndGet();
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                    System.err.println("checkout failed: " + e.getMessage());
                }
            });
        }

        Thread.sleep(50);
        long startedAt = System.nanoTime();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(300, TimeUnit.SECONDS);
        double millis = (System.nanoTime() - startedAt) / 1_000_000.0;

        try (Connection conn = ds.getConnection()) {
            PointCheckout.CheckoutState user1State = checkout.getState(conn, 1L, ITEM_ID);
            PointCheckout.CheckoutState user2State = checkout.getState(conn, 2L, ITEM_ID);

            System.out.println("=== STAGE 2-2: 낙관적 락 checkout ===");
            System.out.printf("시도: %d, 성공: %d, 실패: %d%n", THREADS, success.get(), failed.get());
            System.out.printf("최종 재고 itemId=%d: %d%n", ITEM_ID, user1State.quantity());
            System.out.printf("user 1 포인트: %d%n", user1State.balance());
            System.out.printf("user 2 포인트: %d%n", user2State.balance());
            System.out.printf("응답시간: %.1fms%n", millis);

            int expectedStock = INITIAL_STOCK - success.get();
            int lostUpdates = user1State.quantity() == expectedStock ? 0 : 1;
            MeasurementLog.save("s2-2", "낙관락 version checkout", lostUpdates, failed.get(), millis);
        }

        DataSourceFactory.close(ds);
    }
}
