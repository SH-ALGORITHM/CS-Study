package stage;

import domain.BankAccount;
import infra.DataSourceFactory;
import infra.MeasurementLog;
import infra.SchemaBootstrap;
import infra.TransactionHelper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * STAGE 3 — 격리 수준 4 단계 측정 + 비교.
 *
 * <h3>측정 원칙 (1주차와 동일)</h3>
 * <ol>
 *   <li><b>워밍업</b>: JIT + HikariCP 풀 + JDBC 드라이버 연결 캐시</li>
 *   <li><b>여러 번 평균</b>: GC, autovacuum 영향 평균 내기</li>
 *   <li><b>매 측정 전 테이블 초기화</b>: 이전 측정의 잔여 상태 차단</li>
 *   <li><b>측정 중간 출력 금지</b>: println 한 줄에 동기화 효과</li>
 * </ol>
 *
 * <h3>예상 결과</h3>
 * <pre>
 *   READ_COMMITTED  : Lost Update 발생 (격리 수준이 RMW 를 못 막음)
 *   REPEATABLE_READ : PG 가 동시 수정 시 SQLState 40001 던짐 → 실패 카운트 증가
 *                     (Lost Update 는 거의 0 이지만 실패가 많음)
 *   SERIALIZABLE    : 더 엄격한 직렬화 → 실패 더 많음, TPS 더 낮음
 * </pre>
 *
 * <h3>해석 질문</h3>
 * <ul>
 *   <li>SERIALIZABLE 에서 TPS 가 낮은 이유는?</li>
 *   <li>REPEATABLE_READ 에서 발생한 SQLState 40001 을 실제 서비스라면 어떻게 처리?</li>
 *   <li>본인 도메인 (정확성 우선 vs 처리량 우선) 에 어떤 격리 수준이 적합?</li>
 * </ul>
 */
public class Stage3Measurement {

    private static final int  THREADS         = 50;
    private static final int  ATTEMPTS        = 200;
    private static final long INITIAL_BALANCE = 100;
    private static final long ACCOUNT_ID      = 1L;
    private static final int  RUNS            = 5;

    public static void main(String[] args) throws Exception {
        DataSource ds = DataSourceFactory.create(THREADS);

        // === 워밍업 — 결과 안 봄 ===
        System.out.println("[워밍업] 1 회 실행 (결과 안 봄)");
        runOnce(ds, Connection.TRANSACTION_READ_COMMITTED);
        System.out.println("[워밍업] 완료\n");

        // === 격리 수준 3 단계 측정 (READ_UNCOMMITTED 는 PG 에서 RC 와 동일) ===
        Result rc = measure(ds, Connection.TRANSACTION_READ_COMMITTED,  "READ_COMMITTED");
        Result rr = measure(ds, Connection.TRANSACTION_REPEATABLE_READ, "REPEATABLE_READ");
        Result sr = measure(ds, Connection.TRANSACTION_SERIALIZABLE,    "SERIALIZABLE");

        // === 결과 정리 출력 (측정 끝난 후에만) ===
        System.out.println();
        System.out.println("| 격리 수준         | Lost Update (평균) | 실패 (평균) | 응답시간 (ms) |");
        System.out.println("|------------------|---------------------|-------------|---------------|");
        System.out.printf ("| %-16s | %19.1f | %11.1f | %13.1f |%n",
            "READ_COMMITTED",  rc.avgLost, rc.avgFailed, rc.avgMillis);
        System.out.printf ("| %-16s | %19.1f | %11.1f | %13.1f |%n",
            "REPEATABLE_READ", rr.avgLost, rr.avgFailed, rr.avgMillis);
        System.out.printf ("| %-16s | %19.1f | %11.1f | %13.1f |%n",
            "SERIALIZABLE",    sr.avgLost, sr.avgFailed, sr.avgMillis);

        System.out.println();
        System.out.println("해석 (본인 측정값과 비교해서 직접 채워보기):");
        System.out.println("- READ_COMMITTED  : 격리 수준이 RMW Lost Update 를 막지 못함");
        System.out.println("- REPEATABLE_READ : 동시 수정 충돌 시 SQLState 40001 → 실패 카운트로 잡힘");
        System.out.println("- SERIALIZABLE    : 더 엄격한 직렬화 → 실패 빈도와 응답시간 비교 필요");

        MeasurementLog.save("s3", "READ_COMMITTED",  rc.avgLost, rc.avgFailed, rc.avgMillis);
        MeasurementLog.save("s3", "REPEATABLE_READ", rr.avgLost, rr.avgFailed, rr.avgMillis);
        MeasurementLog.save("s3", "SERIALIZABLE",    sr.avgLost, sr.avgFailed, sr.avgMillis);

        DataSourceFactory.close(ds);
    }

    private static Result measure(DataSource ds, int isoLevel, String name) throws Exception {
        double totalLost = 0;
        double totalFailed = 0;
        double totalMillis = 0;

        for (int run = 0; run < RUNS; run++) {
            RunResult r = runOnce(ds, isoLevel);
            totalLost += r.lostUpdates;
            totalFailed += r.failed;
            totalMillis += r.millis;
        }
        return new Result(totalLost / RUNS, totalFailed / RUNS, totalMillis / RUNS);
    }

    private static RunResult runOnce(DataSource ds, int isoLevel) throws Exception {
        SchemaBootstrap.resetAccount(ds, ACCOUNT_ID, INITIAL_BALANCE);
        BankAccount account = new BankAccount(ACCOUNT_ID);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed  = new AtomicInteger(0);
        CountDownLatch start  = new CountDownLatch(1);

        for (int i = 0; i < ATTEMPTS; i++) {
            executor.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { return; }
                try {
                    boolean ok = TransactionHelper.execute(ds, isoLevel,
                        conn -> account.withdraw(conn, 1));
                    if (ok) success.incrementAndGet();
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

        long finalBalance;
        try (Connection c = ds.getConnection()) {
            finalBalance = account.getBalance(c);
        }
        long withdrawnSum = INITIAL_BALANCE - finalBalance;
        long lostUpdates  = Math.max(0, success.get() - withdrawnSum);

        return new RunResult(lostUpdates, failed.get(), millis);
    }

    private record RunResult(long lostUpdates, int failed, double millis) {}
    private record Result(double avgLost, double avgFailed, double avgMillis) {}
}
