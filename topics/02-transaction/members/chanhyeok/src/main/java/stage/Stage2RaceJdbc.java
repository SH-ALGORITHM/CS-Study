package stage;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import domain.RoomBooking;
import infra.DataSourceFactory;
import infra.MeasurementLog;
import infra.SchemaBootstrap;

public class Stage2RaceJdbc {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 200;
    private static final int ROOM_NO = 101;
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 5, 8);
    private static final LocalDate CHECK_OUT = LocalDate.of(2026, 5, 9);

    public static void main(String[] args) throws SQLException, InterruptedException {
        DataSource ds = DataSourceFactory.create(THREADS);
        SchemaBootstrap.resetRoomBookings(ds);

        RoomBooking roomBooking = new RoomBooking();

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < ATTEMPTS; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    return;
                }

                Connection conn = null;
                try {
                    conn = ds.getConnection();
                    conn.setAutoCommit(false);
                    conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                    boolean ok = roomBooking.tryBook(conn, ROOM_NO, CHECK_IN, CHECK_OUT, "Guest-N");
                    conn.commit();
                    if (ok) {
                        success.incrementAndGet();
                    }
                } catch (SQLException e) {
                    failed.incrementAndGet();
                    if (conn != null) {
                        try {
                            conn.rollback();
                        } catch (SQLException ignore) {
                        }
                    }
                } finally {
                    if (conn != null) {
                        try {
                            conn.setAutoCommit(true);
                            conn.close();
                        } catch (SQLException ignore) {
                        }
                    }
                }
            });
        }

        Thread.sleep(50);

        long t0 = System.nanoTime();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        double millis = (System.nanoTime() - t0) / 1_000_000.0;

        // === 측정 종료 후 결과 출력 ===
        long doubleBookings;
        try (Connection conn = ds.getConnection()) {
            doubleBookings = roomBooking.countBookings(conn, ROOM_NO, CHECK_IN, CHECK_OUT);
        }
        long raceCount = Math.max(0, doubleBookings - 1);

        System.out.println("=== STAGE 2-1: 헬퍼 없이 손으로 (READ_COMMITTED + INSERT race) ===");
        System.out.printf("성공 카운트: %d, 실패: %d%n", success.get(), failed.get());
        System.out.printf("이중 예약 row: %d (정상은 1, 초과분이 race)%n", doubleBookings);
        System.out.printf("Race 발생 (초과 예약): %d%n", raceCount);
        System.out.printf("응답시간: %.1fms%n", millis);

        MeasurementLog.save("s2-1", "JDBC 손으로 (READ_COMMITTED, INSERT race)",
            raceCount, failed.get(), millis);

        DataSourceFactory.close(ds);
    }
}
