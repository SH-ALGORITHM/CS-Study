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
 * STAGE 2-2 — 같은 로직을 {@link TransactionHelper} 로 추출 후.
 *
 * <h3>Stage2RaceJdbc 와 비교</h3>
 * 비즈니스 로직 ({@code account.withdraw(conn, 1)}) 한 줄만 남고
 * try-catch / setAutoCommit / commit/rollback / 풀 반납 정리가 모두 헬퍼 안으로 사라짐.
 *
 * <h3>4 주차 브릿지</h3>
 * 4 주차에서 만나게 될 {@code @Transactional(isolation = ...)} 어노테이션이
 * 정확히 이 헬퍼와 같은 일을 한다 — 메서드 호출을 가로채서 트랜잭션을 열고 닫음.
 * 본인이 만든 헬퍼와 Spring 의 어노테이션을 비교하면 프록시 도입 명분이 보인다.
 *
 * <h3>측정 결과</h3>
 * Stage2RaceJdbc 와 같은 격리 수준 / 같은 도메인 → Lost Update 발생량도 비슷해야 정상.
 * 헬퍼 추출은 코드 가독성만 바꾸지 동작은 변하지 않음.
 */
public class Stage2WithHelper {

    private static final int  THREADS         = 50;
    private static final int  ATTEMPTS        = 200;
    private static final long INITIAL_BALANCE = 100;
    private static final long ACCOUNT_ID      = 1L;

    public static void main(String[] args) throws Exception {
        DataSource ds = DataSourceFactory.create(THREADS);
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
                    boolean ok = TransactionHelper.execute(
                        ds,
                        Connection.TRANSACTION_READ_COMMITTED,
                        conn -> account.withdraw(conn, 1)
                    );
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

        System.out.println("=== STAGE 2-2: TransactionHelper 사용 (READ_COMMITTED + RMW) ===");
        System.out.printf("성공 카운트: %d, 실패: %d%n", success.get(), failed.get());
        System.out.printf("최종 잔고: %d (실제 출금된 양: %d)%n", finalBalance, withdrawnSum);
        System.out.printf("Lost Update: %d%n", lostUpdates);
        System.out.printf("응답시간: %.1fms%n", millis);

        MeasurementLog.save("s2-2", "Helper (READ_COMMITTED)", lostUpdates, failed.get(), millis);

        DataSourceFactory.close(ds);
    }
}
