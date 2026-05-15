package domain;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 계좌 이체 도메인 — 3 주차 락 학습용.
 *
 * <h3>read-modify-write 패턴 강제</h3>
 * {@code SELECT balance} → 앱 계산 → {@code UPDATE balance = ?} 로 짠다.
 * {@code UPDATE balance = balance - ?} 같은 atomic UPDATE 로 짜면
 * PG row-lock 이 자동으로 막아서 락 학습 포인트가 안 보임 (1, 2 주차와 동일 원칙).
 *
 * <h3>제공 메서드 (3 가지 락 별)</h3>
 * <ul>
 *   <li>{@link #transferPessimistic} — SELECT FOR UPDATE</li>
 *   <li>{@link #transferOptimistic} — version 컬럼 + 재시도</li>
 *   <li>{@link #transferDistributed} — Redis SETNX (별도 클래스 호출)</li>
 * </ul>
 */
public class BankAccount {

    private static final String SELECT_FOR_UPDATE_SQL =
        "SELECT balance FROM account WHERE id = ? FOR UPDATE";

    private static final String SELECT_FOR_VERSION_SQL =
        "SELECT balance, version FROM account WHERE id = ?";

    private static final String UPDATE_BALANCE_SQL =
        "UPDATE account SET balance = ? WHERE id = ?";

    private static final String UPDATE_OPTIMISTIC_SQL =
        "UPDATE account SET balance = ?, version = version + 1 WHERE id = ? AND version = ?";

    /**
     * 비관적 락 — 두 row 를 id 작은 것부터 잠근다 (데드락 회피).
     *
     * @return 성공 시 true. 잔액 부족 시 false.
     */
    public boolean transferPessimistic(Connection conn, long fromId, long toId, BigDecimal amount)
            throws SQLException {

        long lockFirst  = Math.min(fromId, toId);
        long lockSecond = Math.max(fromId, toId);

        BigDecimal firstBalance  = selectForUpdate(conn, lockFirst);
        BigDecimal secondBalance = selectForUpdate(conn, lockSecond);

        BigDecimal fromBalance = (fromId == lockFirst) ? firstBalance : secondBalance;
        BigDecimal toBalance   = (toId == lockFirst)   ? firstBalance : secondBalance;

        if (fromBalance.compareTo(amount) < 0) {
            return false;
        }

        updateBalance(conn, fromId, fromBalance.subtract(amount));
        updateBalance(conn, toId, toBalance.add(amount));
        return true;
    }

    /**
     * 낙관적 락 — version 컬럼 비교 후 UPDATE. 충돌 시 재시도.
     *
     * @return 성공 시 true. 잔액 부족 또는 재시도 한계 초과 시 false (starvation).
     */
    public boolean transferOptimistic(Connection conn, long fromId, long toId,
                                       BigDecimal amount, int maxRetries) throws SQLException {

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            VersionedBalance from = selectWithVersion(conn, fromId);
            VersionedBalance to   = selectWithVersion(conn, toId);

            if (from.balance.compareTo(amount) < 0) {
                return false;
            }

            int affectedFrom = updateOptimistic(conn, fromId, from.balance.subtract(amount), from.version);
            if (affectedFrom != 1) {
                // 다른 트랜잭션이 fromId 먼저 UPDATE → 재시도 (fromId 변경 X 이므로 rollback 불필요)
                continue;
            }
            int affectedTo = updateOptimistic(conn, toId, to.balance.add(amount), to.version);
            if (affectedTo != 1) {
                // ⚠️ partial update — fromId 는 이미 차감됐는데 toId 충돌.
                //    rollback 안 하면 다음 attempt 가 차감된 fromId 보고 또 차감 → 정합성 깨짐.
                conn.rollback();
                continue;
            }
            return true;
        }
        return false;   // 재시도 한계 초과 = starvation
    }

    /**
     * 잔액 검증 + UPDATE (분산락 사용 시 외부에서 락 잡고 호출).
     *
     * <p>Redis SETNX 분산락은 {@code Stage2Distributed} 에서 잡고 이 메서드 호출.
     * 즉 이 메서드 자체는 락 책임 없음 — DB 변경만.
     */
    public boolean transferRaw(Connection conn, long fromId, long toId, BigDecimal amount)
            throws SQLException {

        BigDecimal fromBalance = selectBalance(conn, fromId);
        BigDecimal toBalance   = selectBalance(conn, toId);

        if (fromBalance.compareTo(amount) < 0) {
            return false;
        }
        updateBalance(conn, fromId, fromBalance.subtract(amount));
        updateBalance(conn, toId, toBalance.add(amount));
        return true;
    }

    // ---- helpers ----

    private BigDecimal selectForUpdate(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_FOR_UPDATE_SQL)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private VersionedBalance selectWithVersion(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_FOR_VERSION_SQL)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new VersionedBalance(rs.getBigDecimal(1), rs.getLong(2));
            }
        }
    }

    private BigDecimal selectBalance(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT balance FROM account WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private void updateBalance(Connection conn, long id, BigDecimal newBalance) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_BALANCE_SQL)) {
            ps.setBigDecimal(1, newBalance);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private int updateOptimistic(Connection conn, long id, BigDecimal newBalance, long expectedVersion)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_OPTIMISTIC_SQL)) {
            ps.setBigDecimal(1, newBalance);
            ps.setLong(2, id);
            ps.setLong(3, expectedVersion);
            return ps.executeUpdate();
        }
    }

    /** 잔액 조회 — 측정 후 검증용 */
    public BigDecimal getBalance(Connection conn, long id) throws SQLException {
        return selectBalance(conn, id);
    }

    private record VersionedBalance(BigDecimal balance, long version) {}
}
