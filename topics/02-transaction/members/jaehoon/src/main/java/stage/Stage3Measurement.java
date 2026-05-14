package stage;

import domain.CurrencyExchange;
import infra.DataSourceFactory;
import infra.MeasurementLog;
import infra.SchemaBootstrap;

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
    private static final long INITIAL_KRW = 1000000;
    private static final long INITIAL_USD = 1000;
    private static final long USER_ID = 1L;
    private static final long EXCHANGE_AMOUNT = 1000;
    private static final int RUNS = 5;

    public static void main(String[] args) throws Exception {
        MeasurementLog.setAnchorClass(Stage3Measurement.class);

        DataSource ds = DataSourceFactory.create(THREADS);

        // 워밍업
        System.out.println("[워밍업] 1회 실행 (결과 안 봄)");
        runOnce(ds, Connection.TRANSACTION_READ_COMMITTED);
        System.out.println("[워밍업] 완료\n");

        //격리 수준 3단계 측정
        // READ_UNCOMMITTED는 PG에서 RC와 동일하므로 생략
        Result rc = measure(ds, Connection.TRANSACTION_READ_COMMITTED, "READ_COMMITTED");
        Result rr = measure(ds, Connection.TRANSACTION_REPEATABLE_READ, "REPEATABLE_READ");
        Result sr = measure(ds, Connection.TRANSACTION_SERIALIZABLE, "SERIALIZABLE");

        //결과 출력 (측정 끝난 후에만)
        System.out.println();
        System.out.println("=== STAGE 3: 격리 수준별 측정 결과 (환전 도메인, 5회 평균) ===");
        System.out.printf("설정: %d 스레드, %d 시도, 초기 잔고 %,d원, 환전 단위 %,d원%n",
            THREADS, ATTEMPTS, INITIAL_KRW, EXCHANGE_AMOUNT);
        System.out.println();
        System.out.println("| 격리 수준         | Lost Update (평균) | 실패 (평균) | 응답시간 (ms) |");
        System.out.println("|------------------|---------------------|-------------|---------------|");
        System.out.printf("| %-16s | %19.1f | %11.1f | %13.1f |%n",
            "READ_COMMITTED", rc.avgLost, rc.avgFailed, rc.avgMillis);
        System.out.printf("| %-16s | %19.1f | %11.1f | %13.1f |%n",
            "REPEATABLE_READ", rr.avgLost, rr.avgFailed, rr.avgMillis);
        System.out.printf("| %-16s | %19.1f | %11.1f | %13.1f |%n",
            "SERIALIZABLE", sr.avgLost, sr.avgFailed, sr.avgMillis);

        System.out.println();
        System.out.println("해석:");
        System.out.println("- READ_COMMITTED  : SELECT가 lock-free라 RMW Lost Update 못 막음");
        System.out.println("- REPEATABLE_READ : 동시 수정 감지 → SQLState 40001 에러로 거부 (실패↑, 누락↓)");
        System.out.println("- SERIALIZABLE    : SSI로 더 엄격하게 감지 → 실패 더 많음, TPS 비교 필요");
        System.out.println();
        System.out.println("→ 3주차 브릿지: 격리 수준만으로는 '에러+재시도'뿐.");
        System.out.println("  필요한 row에만 락을 거는 것(SELECT FOR UPDATE)이 더 효율적 → 3주차 주제.");

        // 측정 기록
        MeasurementLog.save("s3", "READ_COMMITTED", rc.avgLost, rc.avgFailed, rc.avgMillis);
        MeasurementLog.save("s3", "REPEATABLE_READ", rr.avgLost, rr.avgFailed, rr.avgMillis);
        MeasurementLog.save("s3", "SERIALIZABLE", sr.avgLost, sr.avgFailed, sr.avgMillis);

        DataSourceFactory.close(ds);
    }

    //특정 격리 수준으로 RUNS회 반복 측정, 평균 반환
    private static Result measure(DataSource ds, int isoLevel, String name) throws Exception {
        double totalLost = 0, totalFailed = 0, totalMillis = 0;

        for (int run = 0; run < RUNS; run++) {
            RunResult r = runOnce(ds, isoLevel);
            totalLost += r.lostUpdates;
            totalFailed += r.failed;
            totalMillis += r.millis;
        }
        return new Result(totalLost / RUNS, totalFailed / RUNS, totalMillis / RUNS);
    }

    /**
     * 테이블 초기화 -> 동시 환전 -> Lost Update 계산
     * Stage2RaceJdbc와 동일한 로직, 격리수준 별 측정
     */
    private static RunResult runOnce(DataSource ds, int isoLevel) throws Exception {
        // 매 측정 전 초기화 - 이전 결과 영향 차단
        SchemaBootstrap.resetWallet(ds, USER_ID, INITIAL_KRW, INITIAL_USD);
        CurrencyExchange exchange = new CurrencyExchange(USER_ID);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < ATTEMPTS; i++) {
            executor.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { return; }

                Connection conn = null;
                try {
                    conn = ds.getConnection();
                    conn.setAutoCommit(false);
                    conn.setTransactionIsolation(isoLevel);

                    boolean ok = exchange.withdrawKrw(conn, EXCHANGE_AMOUNT);
                    conn.commit();
                    if (ok) success.incrementAndGet();
                } catch (SQLException e) {
                    // RR/SR에서 SQLState 40001 (serialization_failure) 여기로 옴
                    failed.incrementAndGet();
                    if (conn != null) {
                        try { conn.rollback(); } catch (SQLException ignore) {}
                    }
                } finally {
                    if (conn != null) {
                        try {
                            conn.setAutoCommit(true);
                            conn.close();
                        } catch (SQLException ignore) {}
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

        // Lost Update 계산
        long finalKrw;
        try (Connection c = ds.getConnection()) {
            finalKrw = exchange.getBalance(c, "KRW");
        }
        long actualWithdrawn = INITIAL_KRW - finalKrw;
        long expectedWithdrawn = success.get() * EXCHANGE_AMOUNT;
        long lostUpdates = Math.max(0, expectedWithdrawn - actualWithdrawn) / EXCHANGE_AMOUNT;

        return new RunResult(lostUpdates, failed.get(), millis);
    }

    private record RunResult(long lostUpdates, int failed, double millis) {}
    private record Result(double avgLost, double avgFailed, double avgMillis) {}
}
