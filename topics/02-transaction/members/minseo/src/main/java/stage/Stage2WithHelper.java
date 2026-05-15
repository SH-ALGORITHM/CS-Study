package stage;

import domain.StudyRoomBooking;
import infra.DataSourceFactory;
import infra.MeasurementLog;
import infra.SchemaBootstrap;
import infra.TransactionHelper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Stage2WithHelper {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 200;
    private static final int ROOM_ID = 1;
    private static final String START = "2026-05-14 14:00+09";
    private static final String END = "2026-05-14 15:00+09";

    public static void main(String[] args) throws Exception {
        DataSource ds = DataSourceFactory.create(THREADS);
        SchemaBootstrap.resetBookingTable(ds);

        StudyRoomBooking bookingService = new StudyRoomBooking();

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);

        for (int i = 0; i < ATTEMPTS; i++) {
            final int userId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    boolean ok = TransactionHelper.execute(ds, Connection.TRANSACTION_READ_COMMITTED,
                        conn -> bookingService.bookIfEmpty(conn, ROOM_ID, START, END, "User-" + userId));
                    if (ok) success.incrementAndGet();
                } catch (SQLException e) {
                    failed.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        Thread.sleep(100);
        long t0 = System.nanoTime();
        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        double millis = (System.nanoTime() - t0) / 1_000_000.0;

        long actualBookings;
        try (Connection c = ds.getConnection()) {
            actualBookings = bookingService.countBookings(c, ROOM_ID, START, END);
        }

        long phantomReads = Math.max(0, actualBookings - 1);

        System.out.println("=== STAGE 2-2: TransactionHelper 사용 (READ_COMMITTED) ===");
        System.out.printf("성공 카운트: %d, 실패: %d%n", success.get(), failed.get());
        System.out.printf("실제 생성된 예약 수: %d%n", actualBookings);
        System.out.printf("Phantom Read (중복 예약): %d%n", phantomReads);
        System.out.printf("응답시간: %.1fms%n", millis);

        MeasurementLog.save("s2-2", "TransactionHelper (RC)", phantomReads, failed.get(), millis);

        DataSourceFactory.close(ds);
    }
}
