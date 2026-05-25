package domain;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SeatBooking {

    private static final String SELECT_FOR_UPDATE_SEAT_SQL =
        "SELECT reserved_by FROM seat WHERE id = ? FOR UPDATE";
    private static final String SELECT_FOR_VERSION_SEAT_SQL =
        "SELECT reserved_by, version FROM seat WHERE id = ?";
    private static final String UPDATE_SEAT_SQL =
        "UPDATE seat SET reserved_by = ? WHERE id = ?";
    private static final String UPDATE_OPTIMISTIC_SEAT_SQL =
        "UPDATE seat SET reserved_by = ?, version = version + 1 WHERE id = ? AND version = ?";


    private static final String SELECT_FOR_UPDATE_WALLET_SQL =
        "SELECT balance FROM user_wallet WHERE user_id = ? FOR UPDATE";
    private static final String SELECT_FOR_VERSION_WALLET_SQL =
        "SELECT balance, version FROM user_wallet WHERE user_id = ?";
    private static final String UPDATE_WALLET_SQL =
       "UPDATE user_wallet SET balance = ? WHERE user_id = ?";
    private static final String UPDATE_OPTIMISTIC_WALLET_SQL =
       "UPDATE user_wallet SET balance = ?, version = version + 1 WHERE user_id = ? AND version = ?";


    public boolean bookSeatPessimistic(Connection conn, long seatId, String userId, BigDecimal price)
        throws SQLException {

        // 좌석 조회 및 잠그기
        String reservedBy = selectSeatForUpdate(conn, seatId);

        // 예약 체크
        if (reservedBy != null) return false;

        // 지갑 잠그기
        BigDecimal balance = selectWalletForUpdate(conn, userId);

        // 잔액 충분한지 체크
        if (balance.compareTo(price) < 0) return false;

        // 다 통과하면 업데이트
        updateSeat(conn, seatId, userId);
        updateWallet(conn, userId, balance.subtract(price));

        return true;
    }

    public boolean bookSeatOptimistic(Connection conn, long seatId, String userId, BigDecimal price, int maxRetries) throws SQLException {

        for (int attempt = 0; attempt < maxRetries; attempt++) {

            // 데이터, 버전 읽기 (잠금 X)
            VersionedSeat seat = selectSeatWithVersion(conn, seatId);
            if (seat.reservedBy != null) return false; // 이미 예약(재시도 필요 X)

            VersionedWallet wallet = selectWalletWithVersion(conn, userId);
            if (wallet.balance.compareTo(price) < 0) return false; // 잔액 부족

            // 좌석 업데이트 시도
            int affectedSeat = updateSeatOptimistic(conn, seatId, userId, seat.version);
            if (affectedSeat == 0) {
                // 다른 사람이 사이에 가로챔 -> 재시도
                continue;
            }

            // 지갑 업데이트 시도
            int affectedWallet = updateWalletOptimistic(conn, userId, wallet.balance.subtract(price), wallet.version);
            if (affectedWallet == 0) {
                // 좌석 점유는 성공 but 지갑 충돌 -> 롤백 안하면 좌석만 예약된 채로 재시도하게 됨(정함성 X)

                conn.rollback();
                continue;
            }

            return true;
        }

        return false;   // 재시도 한계 초과 = starvation
    }


    public boolean bookSeatRaw(Connection conn, long seatId, String userId, BigDecimal price) throws SQLException {

        // 일반 조회
        String reservedBy = selectSeat(conn, seatId);
        if (reservedBy != null) return false;

        BigDecimal balance = selectWallet(conn, userId);
        if (balance.compareTo(price) < 0) return false;

        updateSeat(conn, seatId, userId);
        updateWallet(conn, userId, balance.subtract(price));

        return true;
    }


    // ---- helpers ----

    private String selectSeatForUpdate(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_FOR_UPDATE_SEAT_SQL)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("reserved_by");

                throw new SQLException("좌석을 찾을 수 없습니다: " + id);
            }
        }
    }

    private BigDecimal selectWalletForUpdate(Connection conn, String userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_FOR_UPDATE_WALLET_SQL)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal("balance");

                throw new SQLException("지갑을 찾을 수 없습니다: " + userId);
            }
        }
    }

    // 1. 버전과 함께 조회
    private VersionedSeat selectSeatWithVersion(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_FOR_VERSION_SEAT_SQL)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new VersionedSeat(rs.getString("reserved_by"), rs.getLong("version"));
                throw new SQLException("좌석 없음");
            }
        }
    }

    // 2. 버전을 체크하며 업데이트 (성공하면 1, 실패하면 0 반환)
   private int updateSeatOptimistic(Connection conn, long id, String userId, long version) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_OPTIMISTIC_SEAT_SQL)) {
            ps.setString(1, userId);
            ps.setLong(2, id);
            ps.setLong(3, version);
            return ps.executeUpdate();
        }
   }

    // 1. 버전과 함께 조회
    private VersionedWallet selectWalletWithVersion(Connection conn, String userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_FOR_VERSION_WALLET_SQL)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new VersionedWallet(rs.getBigDecimal("balance"), rs.getLong("version"));
                throw new SQLException("지갑 없음");
            }
        }
    }

    // 2. 버전을 체크하며 업데이트 (성공하면 1, 실패하면 0 반환)
    private int updateWalletOptimistic(Connection conn, String userId, BigDecimal balance, long version) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_OPTIMISTIC_WALLET_SQL)) {
            ps.setBigDecimal(1, balance);
            ps.setString(2, userId);
            ps.setLong(3, version);
            return ps.executeUpdate();
        }
    }

    private String selectSeat(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT reserved_by FROM seat WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("reserved_by");
                throw new SQLException("좌석 없음");
            }
        }
    }

    private BigDecimal selectWallet(Connection conn, String userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT balance FROM user_wallet WHERE user_id = ?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal("balance");
                throw new SQLException("지갑 없음");
            }
        }
    }

    private void updateSeat(Connection conn, long seatId, String userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_SEAT_SQL)) {
            ps.setString(1, userId);
            ps.setLong(2, seatId);
            int affected = ps.executeUpdate();

            if (affected == 0) throw new SQLException("좌석 업데이트 실패" + seatId);
        }
    }

    private void updateWallet(Connection conn, String userId, BigDecimal newBalance) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_WALLET_SQL)) {
            ps.setBigDecimal(1, newBalance);
            ps.setString(2, userId);
            int affected = ps.executeUpdate();

            if (affected == 0) throw new SQLException("지갑 업데이트 실패" + userId);
        }
    }

    /** 지갑 잔액 조회 — 측정 후 정합성 검증용 */
   public BigDecimal getWalletBalance(Connection conn, String userId) throws SQLException {
        return selectWallet(conn, userId);
   }

    /** 좌석 예약자 조회 — 측정 후 정합성 검증용 */
    public String getReservedBy(Connection conn, long seatId) throws SQLException {
        return selectSeat(conn, seatId);
    }

    private record VersionedSeat(String reservedBy, long version) {}
    private record VersionedWallet(BigDecimal balance, long version) {}
}
