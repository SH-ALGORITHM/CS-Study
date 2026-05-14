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

/**
 * EXCLUDE 단독 측정 — 다른 격리 수준 측정 없이 EXCLUDE 만 5 회 평균.
 *
 * <h3>가설</h3>
 * Stage3Measurement 에서 EXCLUDE 응답시간 폭증 (47ms → 12k → 24k ms) 의 원인은
 * <strong>RC/RR/SR 측정이 누적시킨 catalog/dead tuple 상태</strong>일 가능성.
 *
 * <h3>실험 방법</h3>
 * <ol>
 *   <li>{@code docker compose down -v} 로 DB 완전 초기화</li>
 *   <li>이 파일 실행 — RC/RR/SR 측정 없이 EXCLUDE 만 측정</li>
 *   <li>응답시간이 정상 (30-50ms) → 가설 입증 (측정 순서 영향)</li>
 *   <li>여전히 폭증 → EXCLUDE 자체의 본질적 비용</li>
 * </ol>
 */
public class Stage3ExcludeOnly {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 200;
    private static final int RUNS = 5;
    private static final int ROOM_NO = 101;
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 5, 8);
    private static final LocalDate CHECK_OUT = LocalDate.of(2026, 5, 9);

    public static void main(String[] args) throws Exception {
        DataSource ds = DataSourceFactory.create(THREADS);
        RoomBooking booking = new RoomBooking();

        System.out.println("[워밍업] 1회 — RC, no EXCLUDE");
        runOnce(ds, booking, Connection.TRANSACTION_READ_COMMITTED, false);
        System.out.println("[워밍업] 완료");

        // ★ EXCLUDE 만 측정 — RC/RR/SR 측정 안 함
        Result exclude = measure(ds, booking, Connection.TRANSACTION_READ_COMMITTED, true, "EXCLUDE_only");

        System.out.println();
        System.out.println("=== EXCLUDE 단독 측정 (" + THREADS + " 스레드 × " + ATTEMPTS + " 시도 × " + RUNS + " 회 평균) ===");
        System.out.println();
        System.out.printf("Race (평균): %.1f%n", exclude.avgRace);
        System.out.printf("실패 (평균): %.1f%n", exclude.avgFailed);
        System.out.printf("응답시간 (평균): %.1f ms%n", exclude.avgMillis);
        System.out.println();

        MeasurementLog.save("s3", "EXCLUDE_only_no_other_cases", exclude.avgRace, exclude.avgFailed, exclude.avgMillis);

        SchemaBootstrap.dropExcludeConstraint(ds);
        DataSourceFactory.close(ds);
    }

    private static Result measure(DataSource ds, RoomBooking booking, int isoLevel, boolean useExclude,
                                  String name) throws Exception {
        SchemaBootstrap.dropExcludeConstraint(ds);
        if (useExclude) {
            SchemaBootstrap.addExcludeConstraint(ds);
        }

        double totalRace = 0, totalFailed = 0, totalMillis = 0;
        for (int run = 0; run < RUNS; run++) {
            RunResult r = runOnce(ds, booking, isoLevel, useExclude);
            totalRace += r.race;
            totalFailed += r.failed;
            totalMillis += r.millis;
        }
        return new Result(totalRace / RUNS, totalFailed / RUNS, totalMillis / RUNS);
    }

    private static RunResult runOnce(DataSource ds, RoomBooking booking, int isoLevel, boolean useExclude) throws Exception {
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
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
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
