package stage;

import domain.BankAccount;
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

/**
 * STAGE 2-1 — JDBC 트랜잭션을 손으로 (헬퍼 없음).
 *
 * <h3>학습 의도</h3>
 * 람다 안에서 {@code conn.setAutoCommit(false)} / {@code commit} / {@code rollback} /
 * try-catch 패턴을 직접 작성. 같은 모양이 반복되는 것을 코드로 보고
 * STAGE 2-2 에서 본인이 헬퍼로 추출.
 *
 * <h3>왜 이렇게 길게 짜야 하나</h3>
 * 4 주차에서 {@code @Transactional} 을 처음 봤을 때 "이게 뭘 자동화하는지"
 * 본인 코드로 알아야 의미가 보인다. 헬퍼부터 쓰면 자동화의 가치를 못 느낀다.
 *
 * <h3>측정 결과 해석</h3>
 * READ_COMMITTED + RMW 패턴이라 Lost Update 가 발생할 것.
 * 200 번 출금 시도 / 잔고 100 / 1 원씩 → 정상이면 success ≤ 100, 잔고 0.
 * race 가 일어나면 success > 실제 출금량 (Lost Update 만큼 차이).
 */
public class Stage2RaceJdbc {

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

                // === 트랜잭션을 손으로 — 같은 패턴이 반복됨을 직접 확인 ===
                Connection conn = null;
                try {
                    conn = ds.getConnection();
                    // 순서 메모: setAutoCommit(false) → setTransactionIsolation 이 관행.
                    // 둘 다 커넥션 단위 설정이라 다음 statement 실행 시점에 적용되므로
                    // 기능적으로 순서가 결과를 바꾸진 않지만, 트랜잭션 경계를 먼저
                    // 명확히 한 뒤 격리 수준을 잡는 게 가독성 / 디버깅에 유리.
                    conn.setAutoCommit(false);
                    conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                    boolean ok = account.withdraw(conn, 1);
                    conn.commit();
                    if (ok) success.incrementAndGet();
                } catch (SQLException e) {
                    failed.incrementAndGet();
                    if (conn != null) {
                        try { conn.rollback(); } catch (SQLException ignore) {}
                    }
                } finally {
                    if (conn != null) {
                        try {
                            conn.setAutoCommit(true);  // 풀 반납 전 원상복구
                            conn.close();
                        } catch (SQLException ignore) {}
                    }
                }
            });
        }

        Thread.sleep(50);  // 모든 task 가 latch 대기 도달까지 잠깐
        long t0 = System.nanoTime();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        double millis = (System.nanoTime() - t0) / 1_000_000.0;

        // === 측정 종료 후 결과 출력 ===
        long finalBalance;
        try (Connection c = ds.getConnection()) {
            finalBalance = account.getBalance(c);
        }
        long withdrawnSum = INITIAL_BALANCE - finalBalance;
        long lostUpdates  = Math.max(0, success.get() - withdrawnSum);

        System.out.println("=== STAGE 2-1: 헬퍼 없이 손으로 (READ_COMMITTED + RMW) ===");
        System.out.printf("성공 카운트: %d, 실패: %d%n", success.get(), failed.get());
        System.out.printf("최종 잔고: %d (실제 출금된 양: %d)%n", finalBalance, withdrawnSum);
        System.out.printf("Lost Update: %d (성공 카운트 - 실제 출금 양)%n", lostUpdates);
        System.out.printf("응답시간: %.1fms%n", millis);

        MeasurementLog.save("s2-1", "JDBC 손으로 (READ_COMMITTED)", lostUpdates, failed.get(), millis);

        DataSourceFactory.close(ds);
    }
}
