package domain;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * P2P 송금 도메인 — 3 주차 락 학습용.
 *
 * <h3>row 3 개 동시 변경</h3>
 * 송금 1 건 처리 시 다음 row 가 한 트랜잭션 안에서 모두 변경되어야 정합성 유지:
 * <ul>
 *   <li>user_wallet 송금자 — balance ↓ + daily_sent_amount ↑</li>
 *   <li>user_wallet 수취자 — balance ↑</li>
 *   <li>fee_revenue (id=1) — total_collected ↑ (핫스팟)</li>
 *   <li>transfer_log — INSERT 1 건</li>
 * </ul>
 *
 * <h3>락 순서 (데드락 회피)</h3>
 * {@code min(fromId, toId) → max(fromId, toId) → fee_revenue(id=1)}.
 * 모든 트랜잭션이 동일한 순서로 락을 잡으면 순환 대기 X → 데드락 X.
 *
 * <h3>read-modify-write 패턴 강제</h3>
 * {@code SELECT balance, daily_sent_amount} → 앱에서 검증 → {@code UPDATE} 로 짠다.
 * {@code UPDATE balance = balance - ?} 같은 atomic UPDATE 로 짜면
 * PG row-lock 이 자동으로 막아서 락 학습 포인트가 안 보임 (1, 2 주차 동일 원칙).
 *
 * <h3>일일 한도 검증</h3>
 * 잔액 검증 + 일일 한도 검증 2 단계 (RMW 단계 추가). 한도 초과 시 false 반환.
 */
public class P2PWallet {

    /** 1 일 송금 한도 — RMW 검증 대상 */
    public static final BigDecimal DAILY_LIMIT = new BigDecimal("500000");
    /** 송금 수수료 — 측정 단순화를 위해 고정 */
    public static final BigDecimal FEE = new BigDecimal("10");

    private static final String SELECT_WALLET_FOR_UPDATE_SQL =
        "SELECT balance, daily_sent_amount FROM user_wallet WHERE id = ? FOR UPDATE";

    private static final String SELECT_WALLET_VERSION_SQL =
        "SELECT balance, daily_sent_amount, version FROM user_wallet WHERE id = ?";

    private static final String SELECT_FEE_FOR_UPDATE_SQL =
        "SELECT total_collected FROM fee_revenue WHERE id = 1 FOR UPDATE";

    private static final String SELECT_FEE_VERSION_SQL =
        "SELECT total_collected, version FROM fee_revenue WHERE id = 1";

    private static final String UPDATE_WALLET_SQL =
        "UPDATE user_wallet SET balance = ?, daily_sent_amount = ? WHERE id = ?";

    private static final String UPDATE_WALLET_OPTIMISTIC_SQL =
        "UPDATE user_wallet SET balance = ?, daily_sent_amount = ?, version = version + 1 " +
        "WHERE id = ? AND version = ?";

    private static final String UPDATE_FEE_SQL =
        "UPDATE fee_revenue SET total_collected = ? WHERE id = 1";

    private static final String UPDATE_FEE_OPTIMISTIC_SQL =
        "UPDATE fee_revenue SET total_collected = ?, version = version + 1 " +
        "WHERE id = 1 AND version = ?";

    private static final String INSERT_LOG_SQL =
        "INSERT INTO transfer_log (from_id, to_id, amount, fee) VALUES (?, ?, ?, ?)";

    /**
     * 비관적 락 — row 3 개를 정해진 순서로 잠근다.
     *
     * @return 성공 시 true. 잔액 부족 / 일일 한도 초과 시 false.
     */
    public boolean transferPessimistic(Connection conn, long fromId, long toId, BigDecimal amount)
            throws SQLException {

        long lockFirst = Math.min(fromId, toId);
        long lockSecond = Math.max(fromId, toId);

        Wallet first = selectForUpdate(conn, lockFirst);
        Wallet second = selectForUpdate(conn, lockSecond);
        BigDecimal feeTotal = selectFeeForUpdate(conn);

        Wallet from = (fromId == lockFirst) ? first : second;
        Wallet to = (toId == lockFirst) ? first : second;

        if (from.balance.compareTo(amount.add(FEE)) < 0) return false;
        if (from.dailySent.add(amount).compareTo(DAILY_LIMIT) > 0) return false;

        updateWallet(conn, fromId, from.balance.subtract(amount).subtract(FEE), from.dailySent.add(amount));
        updateWallet(conn, toId, to.balance.add(amount), to.dailySent);
        updateFee(conn, feeTotal.add(FEE));
        insertLog(conn, fromId, toId, amount, FEE);
        return true;
    }

    /**
     * 낙관적 락 — 3 개 row 의 version 비교 후 UPDATE. 충돌 시 재시도.
     *
     * <p>partial update (일부 row UPDATE 성공 / 일부 실패) 시 반드시 rollback —
     * 안 그러면 다음 attempt 가 부분 반영된 상태를 다시 읽어서 정합성 깨짐.
     *
     * @return 성공 시 true. 잔액 부족 / 한도 초과 / 재시도 한계 초과 시 false (starvation).
     */
    public boolean transferOptimistic(Connection conn, long fromId, long toId,
                                       BigDecimal amount, int maxRetries) throws SQLException {

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            WalletV from = selectWithVersion(conn, fromId);
            WalletV to = selectWithVersion(conn, toId);
            FeeV feeV = selectFeeWithVersion(conn);

            if (from.balance.compareTo(amount.add(FEE)) < 0) return false;
            if (from.dailySent.add(amount).compareTo(DAILY_LIMIT) > 0) return false;

            int affFrom = updateWalletOptimistic(conn, fromId,
                from.balance.subtract(amount).subtract(FEE),
                from.dailySent.add(amount),
                from.version);
            if (affFrom != 1) {
                // fromId 변경 X — rollback 불필요. 재시도.
                continue;
            }

            int affTo = updateWalletOptimistic(conn, toId,
                to.balance.add(amount), to.dailySent, to.version);
            if (affTo != 1) {
                // ⚠️ partial — fromId 이미 차감. rollback 안 하면 정합성 깨짐.
                conn.rollback();
                continue;
            }

            int affFee = updateFeeOptimistic(conn, feeV.total.add(FEE), feeV.version);
            if (affFee != 1) {
                // ⚠️ partial — 두 wallet 이미 변경. rollback 필수.
                conn.rollback();
                continue;
            }

            insertLog(conn, fromId, toId, amount, FEE);
            return true;
        }
        return false;
    }

    /**
     * 락 없이 RMW (분산락 stage 에서 외부 락 잡고 호출).
     */
    public boolean transferRaw(Connection conn, long fromId, long toId, BigDecimal amount)
            throws SQLException {

        Wallet from = selectWallet(conn, fromId);
        Wallet to = selectWallet(conn, toId);
        BigDecimal feeTotal = selectFee(conn);

        if (from.balance.compareTo(amount.add(FEE)) < 0) return false;
        if (from.dailySent.add(amount).compareTo(DAILY_LIMIT) > 0) return false;

        updateWallet(conn, fromId, from.balance.subtract(amount).subtract(FEE), from.dailySent.add(amount));
        updateWallet(conn, toId, to.balance.add(amount), to.dailySent);
        updateFee(conn, feeTotal.add(FEE));
        insertLog(conn, fromId, toId, amount, FEE);
        return true;
    }

    // ---- read helpers ----

    private Wallet selectForUpdate(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_WALLET_FOR_UPDATE_SQL)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new Wallet(rs.getBigDecimal(1), rs.getBigDecimal(2));
            }
        }
    }

    private Wallet selectWallet(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT balance, daily_sent_amount FROM user_wallet WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new Wallet(rs.getBigDecimal(1), rs.getBigDecimal(2));
            }
        }
    }

    private WalletV selectWithVersion(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_WALLET_VERSION_SQL)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new WalletV(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getLong(3));
            }
        }
    }

    private BigDecimal selectFeeForUpdate(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_FEE_FOR_UPDATE_SQL);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getBigDecimal(1);
        }
    }

    private BigDecimal selectFee(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT total_collected FROM fee_revenue WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getBigDecimal(1);
        }
    }

    private FeeV selectFeeWithVersion(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_FEE_VERSION_SQL);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return new FeeV(rs.getBigDecimal(1), rs.getLong(2));
        }
    }

    // ---- write helpers ----

    private void updateWallet(Connection conn, long id, BigDecimal balance, BigDecimal dailySent)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_WALLET_SQL)) {
            ps.setBigDecimal(1, balance);
            ps.setBigDecimal(2, dailySent);
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

    private int updateWalletOptimistic(Connection conn, long id, BigDecimal balance,
                                        BigDecimal dailySent, long expectedVersion) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_WALLET_OPTIMISTIC_SQL)) {
            ps.setBigDecimal(1, balance);
            ps.setBigDecimal(2, dailySent);
            ps.setLong(3, id);
            ps.setLong(4, expectedVersion);
            return ps.executeUpdate();
        }
    }

    private void updateFee(Connection conn, BigDecimal newTotal) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_FEE_SQL)) {
            ps.setBigDecimal(1, newTotal);
            ps.executeUpdate();
        }
    }

    private int updateFeeOptimistic(Connection conn, BigDecimal newTotal, long expectedVersion)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_FEE_OPTIMISTIC_SQL)) {
            ps.setBigDecimal(1, newTotal);
            ps.setLong(2, expectedVersion);
            return ps.executeUpdate();
        }
    }

    private void insertLog(Connection conn, long fromId, long toId, BigDecimal amount, BigDecimal fee)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_LOG_SQL)) {
            ps.setLong(1, fromId);
            ps.setLong(2, toId);
            ps.setBigDecimal(3, amount);
            ps.setBigDecimal(4, fee);
            ps.executeUpdate();
        }
    }

    // ---- 측정 후 검증용 ----

    public BigDecimal balanceOf(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT balance FROM user_wallet WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    public BigDecimal feeTotal(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT total_collected FROM fee_revenue WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getBigDecimal(1);
        }
    }

    private record Wallet(BigDecimal balance, BigDecimal dailySent) {}
    private record WalletV(BigDecimal balance, BigDecimal dailySent, long version) {}
    private record FeeV(BigDecimal total, long version) {}
}
