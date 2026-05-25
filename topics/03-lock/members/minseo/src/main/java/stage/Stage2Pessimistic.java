package stage;

import domain.SeatBooking;
import infra.DataSourceFactory;
import infra.MeasurementLog;
import infra.SchemaBootstrap;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * STAGE 2-1. 비관적 락 (SELECT FOR UPDATE).
 *
 * <p>50 스레드가 동시에 좌석 1번 예약 시도.
 * 좌석 락(Seat) -> 지갑 락(Wallet) 순서로 점유하여 데드락 방지.
 */
public class Stage2Pessimistic {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 200;
    private static final int RUNS = 5;
    private static final long SEAT_ID = 1L;
    private static final String USER_ID = "UserA";
    private static final BigDecimal PRICE = new BigDecimal("1000");

    public static void main(String[] args) throws Exception {
        DataSource ds = DataSourceFactory.create(THREADS);
        SeatBooking booking = new SeatBooking();

        // 워밍업 1 회
        System.out.println("[워밍업] 1 회");
        runOnce(ds, booking);

        double totalMisses = 0, totalFailed = 0, totalMillis = 0;
        for (int run = 0; run < RUNS; run++) {
            RunResult r = runOnce(ds, booking);
            System.out.printf("  RUN %d: misses=%d / failed=%d / %.1f ms%n",
                run, r.misses, r.failed, r.millis);
            totalMisses += r.misses;
            totalFailed += r.failed;
            totalMillis += r.millis;
        }

        double avgMisses = totalMisses / RUNS;
        double avgFailed = totalFailed / RUNS;
        double avgMillis = totalMillis / RUNS;

        System.out.println();
        System.out.printf("=== STAGE 2-1 비관적 락 (FOR UPDATE) — %d 스레드 × %d 시도 × %d 회 평균 ===%n",
            THREADS, ATTEMPTS, RUNS);
        System.out.printf("누락 %.1f / 실패 %.1f / 응답 %.1f ms%n",
            avgMisses, avgFailed, avgMillis);

        MeasurementLog.save("s2-1", "비관락 FOR UPDATE", avgMisses, avgFailed, avgMillis);

        DataSourceFactory.close(ds);
    }

    private static RunResult runOnce(DataSource ds, SeatBooking booking) throws Exception {
        SchemaBootstrap.resetAlls(ds);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < ATTEMPTS; i++) {
            executor.submit(() -> {
                try { start.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                Connection conn = null;
                try {
                    conn = ds.getConnection();
                    conn.setAutoCommit(false);

                    boolean ok = booking.bookSeatPessimistic(conn, SEAT_ID, USER_ID, PRICE);
                    conn.commit();
                    if (ok) success.incrementAndGet();
                } catch (SQLException e) {
                    failed.incrementAndGet();
                    if (conn != null) try { conn.rollback(); } catch (SQLException ignore) {}
                } finally {
                    if (conn != null) try {
                        conn.setAutoCommit(true);
                        conn.close();
                    } catch (SQLException ignore) {}
                }
            });
        }

        Thread.sleep(50);
        long t0 = System.nanoTime();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(300, TimeUnit.SECONDS);
        double millis = (System.nanoTime() - t0) / 1_000_000.0;

        // 검증: 좌석은 예약되어 있어야 하고, 지갑은 정확히 1번 차감되어야 함
        int misses = 0;
        try (Connection conn = ds.getConnection()) {
            String reservedBy = booking.getReservedBy(conn, SEAT_ID);
            BigDecimal balance = booking.getWalletBalance(conn, USER_ID);

            // 좌석이 예약되지 않았거나, 지갑 잔액이 100000 - 1000이 아니면 누락/오류
            if (reservedBy == null || balance.compareTo(new BigDecimal("99000")) != 0) {
                misses = 1;
            }
            
            // 추가 검증: 성공 횟수는 정확히 1이어야 함 (중복 예약 방지 확인)
            if (success.get() != 1) {
                misses = 1;
            }
        }

        return new RunResult(misses, failed.get(), millis);
    }

    private record RunResult(int misses, int failed, double millis) {}
}
