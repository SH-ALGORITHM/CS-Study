package domain;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 환전 흐름 (RMW):
 * 1. SELECT balance WHERE currency='KRW'
 * 2. 앱에서 계산: newKrw = krw - amount
 * 3. UPDATE SET balance = newKrw
 *
 * 1,3 사이에 다른 트랜잭션이 끼어들면 Lost Update 발생
 */
public class CurrencyExchange {

    private final long userId;

    public CurrencyExchange(long userId) {
        this.userId = userId;
    }


    //KRW 잔고에서 amount만큼 차감 (RMW 패턴).
    public boolean withdrawKrw(Connection conn, long amount) throws SQLException {
        // SELECT
        long currentKrw = getBalance(conn, "KRW");

        // 2잔고 부족 체크
        if (currentKrw < amount) {
            return false;
        }

        // 3 계산
        long newKrw = currentKrw - amount;
        updateBalance(conn, "KRW", newKrw);
        return true;
    }


    public long getBalance(Connection conn, String currency) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT balance FROM wallet WHERE user_id = ? AND currency = ?")) {
            ps.setLong(1, userId);
            ps.setString(2, currency);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return 0;
                return rs.getLong("balance");
            }
        }
    }

    private void updateBalance(Connection conn, String currency, long newBalance) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "UPDATE wallet SET balance = ? WHERE user_id = ? AND currency = ?")) {
            ps.setLong(1, newBalance);
            ps.setLong(2, userId);
            ps.setString(3, currency);
            ps.executeUpdate();
        }
    }
}
