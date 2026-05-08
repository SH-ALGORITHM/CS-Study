package stage;

import infra.DataSourceFactory;
import infra.MeasurementLog;
import infra.SchemaBootstrap;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * STAGE 4 (선택) — 데드락 직접 재현.
 *
 * <h3>왜 데드락이 일어나는가</h3>
 * 두 트랜잭션이 같은 row 두 개를 <b>다른 순서로</b> 잡으면 서로 상대를 기다려 데드락.
 *
 * <pre>
 *   T1: BEGIN; UPDATE id=1 (락 잡음); UPDATE id=2 (T2 가 잡고 있음 → 대기)
 *   T2: BEGIN; UPDATE id=2 (락 잡음); UPDATE id=1 (T1 이 잡고 있음 → 대기)
 *   → PG 가 deadlock_timeout (기본 1 초) 후 감지, 한쪽을 abort
 * </pre>
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>PG 가 던지는 SQLState 는? (콘솔 출력 확인 후 measurements.md 에 기록)</li>
 *   <li>두 트랜잭션 중 어느 쪽이 죽는가?</li>
 *   <li>{@code deadlock_timeout} 기본값이 1 초인데 왜 응답이 더 빨리 오기도 하는가?</li>
 * </ul>
 */
public class Stage4Deadlock {

    private static final int  ROUNDS         = 50;  // 각 스레드가 시도할 횟수
    private static final long INITIAL_BALANCE = 10000L;

    public static void main(String[] args) throws Exception {
        DataSource ds = DataSourceFactory.create(4);
        SchemaBootstrap.resetTwoAccounts(ds, INITIAL_BALANCE);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger deadlocks = new AtomicInteger(0);
        AtomicInteger otherErrors = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);

        // T1: 1 → 2 순서로 갱신
        executor.submit(() -> {
            try { start.await(); } catch (InterruptedException e) { return; }
            transferRepeatedly(ds, 1L, 2L, successes, deadlocks, otherErrors);
        });
        // T2: 2 → 1 순서로 갱신 (의도적 역순)
        executor.submit(() -> {
            try { start.await(); } catch (InterruptedException e) { return; }
            transferRepeatedly(ds, 2L, 1L, successes, deadlocks, otherErrors);
        });

        long t0 = System.nanoTime();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);
        double millis = (System.nanoTime() - t0) / 1_000_000.0;

        System.out.println("=== STAGE 4: 데드락 재현 (각 스레드 " + ROUNDS + "회 시도) ===");
        System.out.printf("성공: %d%n", successes.get());
        System.out.printf("데드락: %d (PG 가 한쪽을 abort 한 횟수)%n", deadlocks.get());
        System.out.printf("기타 에러: %d%n", otherErrors.get());
        System.out.printf("총 응답시간: %.1fms%n", millis);
        System.out.println();
        System.out.println("→ 콘솔에 출력된 SQLState 가 무엇인지 measurements.md 에 기록");
        System.out.println("→ deadlock_timeout 기본값은 1초. 응답시간 / 횟수 로 평균 확인");

        // s4 의미 매핑: misses 슬롯에 데드락 횟수 (s4 의 핵심 지표),
        // failed 슬롯에 기타 에러 (예: 40001 외 SQLException),
        // method 이름에 성공 횟수 포함해서 한 줄에 모든 정보 보존.
        MeasurementLog.save(
            "s4",
            "데드락 재현 (성공 " + successes.get() + ")",
            deadlocks.get(),
            otherErrors.get(),
            millis
        );

        DataSourceFactory.close(ds);
    }

    private static void transferRepeatedly(DataSource ds, long firstId, long secondId,
                                           AtomicInteger successes,
                                           AtomicInteger deadlocks,
                                           AtomicInteger otherErrors) {
        for (int i = 0; i < ROUNDS; i++) {
            Connection conn = null;
            try {
                conn = ds.getConnection();
                conn.setAutoCommit(false);
                try {
                    // 첫 번째 row 갱신 → 락 획득
                    updateBalance(conn, firstId, -100);
                    // 다른 트랜잭션이 두 번째 row 를 잡을 시간 확보 (의도적 sleep)
                    Thread.sleep(20);
                    // 두 번째 row 갱신 → 다른 트랜잭션이 잡고 있으면 대기 → 데드락
                    updateBalance(conn, secondId, +100);
                    conn.commit();
                    successes.incrementAndGet();
                } catch (SQLException e) {
                    try { conn.rollback(); } catch (SQLException ignore) {}
                    String state = e.getSQLState();
                    System.err.println("SQLState=" + state + " message=" + e.getMessage());
                    // PG 데드락 감지 시 SQLState 직접 확인 후 분류
                    if (isDeadlockState(state)) {
                        deadlocks.incrementAndGet();
                    } else {
                        otherErrors.incrementAndGet();
                    }
                }
            } catch (SQLException | InterruptedException e) {
                otherErrors.incrementAndGet();
            } finally {
                if (conn != null) {
                    try {
                        conn.setAutoCommit(true);
                        conn.close();
                    } catch (SQLException ignore) {}
                }
            }
        }
    }

    private static void updateBalance(Connection conn, long id, long delta) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "UPDATE account SET balance = balance + ? WHERE id = ?")) {
            ps.setLong(1, delta);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    /**
     * PG 데드락 감지 SQLState 비교.
     * (학습자는 콘솔 출력으로 확인 후 본인 measurements.md 에 기록.)
     */
    private static boolean isDeadlockState(String sqlState) {
        return "40P01".equals(sqlState);
    }
}
