package stage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
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

public class Stage3Measurement {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 200;
    private static final int RUNS = 5;
    private static final int ROOM_NO = 101;
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 5, 8);
    private static final LocalDate CHECK_OUT = LocalDate.of(2026, 5, 9);

    public static void main(String[] args) throws Exception {
        DataSource ds = DataSourceFactory.create(THREADS);
        RoomBooking booking = new RoomBooking();

        System.out.println("[워밍업] 1회");
        runOnce(ds, booking, Connection.TRANSACTION_READ_COMMITTED, false);
        System.out.println("[워밍업] 완료");
        Result rc = measure(ds, booking, Connection.TRANSACTION_READ_COMMITTED, false, "READ_COMMITTED");
        Result rr = measure(ds, booking, Connection.TRANSACTION_REPEATABLE_READ, false, "REPEATABLE_READ");
        Result sr = measure(ds, booking, Connection.TRANSACTION_SERIALIZABLE, false, "SERIALIZABLE");
        Result exclude = measure(ds, booking, Connection.TRANSACTION_READ_COMMITTED, true, "EXCLUDE_constraint");

        System.out.println();
        System.out.println("=== STAGE 3 — 격리 수준 비교 (" + THREADS + " 스레드 × " + ATTEMPTS + " 시도 × " + RUNS + " 회 평균) ===");
        System.out.println();
        System.out.println("| 케이스                | Race (평균) | 실패 (평균) | 응답시간 (ms) |");
        System.out.println("|-----------------------|-------------|-------------|---------------|");
        System.out.printf ("| %-21s | %11.1f | %11.1f | %13.1f |%n", "READ_COMMITTED",     rc.avgRace,      rc.avgFailed,      rc.avgMillis);
        System.out.printf ("| %-21s | %11.1f | %11.1f | %13.1f |%n", "REPEATABLE_READ",    rr.avgRace,      rr.avgFailed,      rr.avgMillis);
        System.out.printf ("| %-21s | %11.1f | %11.1f | %13.1f |%n", "SERIALIZABLE",       sr.avgRace,      sr.avgFailed,      sr.avgMillis);
        System.out.printf ("| %-21s | %11.1f | %11.1f | %13.1f |%n", "EXCLUDE constraint", exclude.avgRace, exclude.avgFailed, exclude.avgMillis);
        System.out.println();

        // === measurements.md 자동 누적 ===
        MeasurementLog.save("s3", "READ_COMMITTED",     rc.avgRace,      rc.avgFailed,      rc.avgMillis);
        MeasurementLog.save("s3", "REPEATABLE_READ",    rr.avgRace,      rr.avgFailed,      rr.avgMillis);
        MeasurementLog.save("s3", "SERIALIZABLE",       sr.avgRace,      sr.avgFailed,      sr.avgMillis);
        MeasurementLog.save("s3", "EXCLUDE_constraint", exclude.avgRace, exclude.avgFailed, exclude.avgMillis);

        // 정리
        SchemaBootstrap.dropExcludeConstraint(ds);

        DataSourceFactory.close(ds);
    }

    private static Result measure(DataSource ds, RoomBooking booking, int isoLevel, boolean useExclude,
                                  String name) throws Exception {
        double totalRace = 0, totalFailed = 0, totalMillis = 0;
        for (int run = 0; run < RUNS; run++) {
            RunResult r = runOnce(ds, booking, isoLevel, useExclude);
            totalRace += r.race;
            totalFailed += r.failed;
            totalMillis += r.millis;
        }
        return new Result(totalRace / RUNS, totalFailed / RUNS, totalMillis / RUNS);
    }

    private static RunResult runOnce(DataSource ds, RoomBooking booking,
                                     int isoLevel, boolean useExclude) throws Exception {
        SchemaBootstrap.dropExcludeConstraint(ds);
        if (useExclude) {
            SchemaBootstrap.addExcludeConstraint(ds);
        }
        SchemaBootstrap.resetRoomBookings(ds);

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
                    conn.setTransactionIsolation(isoLevel);

                    boolean ok = booking.tryBook(conn, ROOM_NO, CHECK_IN, CHECK_OUT, "Guest");
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
        executor.awaitTermination(60, TimeUnit.SECONDS);
        double millis = (System.nanoTime() - t0) / 1_000_000.0;

        int race;
        try (Connection conn = ds.getConnection()) {
            int count = booking.countBookings(conn, ROOM_NO, CHECK_IN, CHECK_OUT);
            race = Math.max(0, count - 1);
        }

        return new RunResult(race, failed.get(), millis);
    }

    private record RunResult(int race, int failed, double millis) {
    }

    private record Result(double avgRace, double avgFailed, double avgMillis) {
    }
}
