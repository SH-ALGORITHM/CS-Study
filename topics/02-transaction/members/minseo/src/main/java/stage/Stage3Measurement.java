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

public class Stage3Measurement {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 200;
    private static final int ROOM_ID = 1;
    private static final String START = "2026-05-14 14:00+09";
    private static final String END = "2026-05-14 15:00+09";

    public static void main(String[] args) throws Exception {
        DataSource ds = DataSourceFactory.create(THREADS);

        // 워밍업
        System.out.println("워밍업 중...");
        runMeasurement(ds, Connection.TRANSACTION_READ_COMMITTED, "WARMUP", false);

        // 정식 측정
        System.out.println("정식 측정 시작...");
        runMeasurement(ds, Connection.TRANSACTION_READ_COMMITTED, "READ_COMMITTED", true);
        runMeasurement(ds, Connection.TRANSACTION_REPEATABLE_READ, "REPEATABLE_READ", true);
        runMeasurement(ds, Connection.TRANSACTION_SERIALIZABLE, "SERIALIZABLE", true);

        DataSourceFactory.close(ds);
    }

    private static void runMeasurement(DataSource ds, int isolationLevel, String label, boolean saveLog) throws Exception {
        StudyRoomBooking bookingService = new StudyRoomBooking();
        
        // 5회 평균을 위해 반복 (여기서는 단순화를 위해 1회 측정 후 기록하거나 시나리오대로 5회 평균 로직 추가 가능)
        // 시나리오에서 5회 평균 권장하므로 루프 돌림
        int iterations = 5;
        double totalMillis = 0;
        double totalPhantomReads = 0;
        double totalFailed = 0;

        for (int iter = 0; iter < iterations; iter++) {
            SchemaBootstrap.resetBookingTable(ds);
            
            ExecutorService executor = Executors.newFixedThreadPool(THREADS);
            AtomicInteger success = new AtomicInteger(0);
            AtomicInteger failed = new AtomicInteger(0);
            CountDownLatch startLatch = new CountDownLatch(1);

            for (int i = 0; i < ATTEMPTS; i++) {
                final int userId = i + (iter * 1000);
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        boolean ok = TransactionHelper.execute(ds, isolationLevel,
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
            totalMillis += (System.nanoTime() - t0) / 1_000_000.0;

            long actualBookings;
            try (Connection c = ds.getConnection()) {
                actualBookings = bookingService.countBookings(c, ROOM_ID, START, END);
            }
            totalPhantomReads += Math.max(0, actualBookings - 1);
            totalFailed += failed.get();
        }

        double avgMillis = totalMillis / iterations;
        double avgPhantomReads = totalPhantomReads / iterations;
        double avgFailed = totalFailed / iterations;

        System.out.printf("[%s] 평균 응답시간: %.1fms, 평균 Phantom Read: %.1f, 평균 실패: %.1f%n",
            label, avgMillis, avgPhantomReads, avgFailed);

        if (saveLog) {
            MeasurementLog.save("s3", label, avgPhantomReads, avgFailed, avgMillis);
        }
    }
}
