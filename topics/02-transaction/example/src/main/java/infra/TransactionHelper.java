package infra;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * STAGE 2-2 의 결과물 — 트랜잭션 boilerplate 를 한 곳에 모은 헬퍼.
 *
 * <h3>학습 흐름상 위치</h3>
 * STAGE 2-1 에서 학습자가 {@code conn.setAutoCommit(false)} / {@code commit} /
 * {@code rollback} / try-catch 패턴을 손으로 5 번 반복한 뒤,
 * 본인이 추출하는 헬퍼의 정답 참고용. 이 파일을 먼저 보고 베끼지 말 것.
 *
 * <h3>설계 포인트</h3>
 * <ul>
 *   <li>checked exception ({@link SQLException}) 을 람다가 던질 수 있어야 함
 *       → {@link Runnable} 못 씀, 직접 함수형 인터페이스 정의 필요</li>
 *   <li>{@code finally} 에서 격리 수준 / autoCommit 원래 값 복원
 *       → 안 하면 풀에 반납된 커넥션이 다음 사용자에게 영향</li>
 *   <li>예외 발생 시 rollback → 안 하면 트랜잭션이 열린 채 풀로 돌아감</li>
 * </ul>
 *
 * <h3>4 주차와의 연결</h3>
 * Spring 의 {@code @Transactional} 이 자동화하는 일이 정확히 이것.
 * 프록시가 메서드 호출을 가로채서 setAutoCommit(false) → 메서드 실행 → commit/rollback.
 * 4 주차에서 어노테이션을 봤을 때 "내가 만든 이 헬퍼의 강화판" 으로 인식할 수 있어야 함.
 */
public final class TransactionHelper {

    @FunctionalInterface
    public interface SqlAction<T> {
        T run(Connection conn) throws SQLException;
    }

    private TransactionHelper() {}

    /**
     * 주어진 격리 수준으로 트랜잭션을 열고 action 실행, 성공 시 commit / 실패 시 rollback.
     *
     * @param ds            커넥션 풀
     * @param isolationLevel {@link Connection#TRANSACTION_READ_COMMITTED} 등
     * @param action        트랜잭션 안에서 실행할 작업
     * @return action 의 반환값
     * @throws SQLException action 또는 commit 중 발생한 예외 (rollback 후 다시 던짐)
     */
    public static <T> T execute(DataSource ds, int isolationLevel, SqlAction<T> action) throws SQLException {
        try (Connection conn = ds.getConnection()) {
            int previousIso = conn.getTransactionIsolation();
            boolean previousAuto = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                conn.setTransactionIsolation(isolationLevel);
                T result = action.run(conn);
                conn.commit();
                return result;
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignore) {}
                throw e;
            } finally {
                // 풀 반납 전 원상복구
                try { conn.setTransactionIsolation(previousIso); } catch (SQLException ignore) {}
                try { conn.setAutoCommit(previousAuto); } catch (SQLException ignore) {}
            }
        }
    }
}
