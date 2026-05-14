package infra;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public final class SchemaBootstrap {

    private SchemaBootstrap() {}

    /**
     * wallet 테이블 생성 + 단일 사용자의 KRW/USD 잔고 초기화.
     * 측정 전 매번 호출해서 깨끗한 상태로 리셋.
     */
    public static void resetWallet(DataSource ds, long userId, long krwBalance, long usdBalance) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS wallet (
                    user_id  BIGINT NOT NULL,
                    currency VARCHAR(3) NOT NULL,
                    balance  NUMERIC NOT NULL,
                    PRIMARY KEY (user_id, currency)
                )
                """);
            s.execute("DELETE FROM wallet WHERE user_id = " + userId);
            try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO wallet (user_id, currency, balance) VALUES (?, ?, ?)")) {
                ps.setLong(1, userId);
                ps.setString(2, "KRW");
                ps.setLong(3, krwBalance);
                ps.executeUpdate();

                ps.setLong(1, userId);
                ps.setString(2, "USD");
                ps.setLong(3, usdBalance);
                ps.executeUpdate();
            }
        }
    }

    /**
     * 데드락 테스트용 — 두 사용자의 KRW/USD 잔고 초기화.
     * A가 KRW→USD, B가 USD→KRW 환전 시 다른 순서로 row 잡아 데드락 발생.
     */
    public static void resetTwoUsers(DataSource ds, long krwBalance, long usdBalance) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS wallet (
                    user_id  BIGINT NOT NULL,
                    currency VARCHAR(3) NOT NULL,
                    balance  NUMERIC NOT NULL,
                    PRIMARY KEY (user_id, currency)
                )
                """);
            s.execute("TRUNCATE wallet");
            try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO wallet (user_id, currency, balance) VALUES (?, ?, ?)")) {
                // user 1
                ps.setLong(1, 1L); ps.setString(2, "KRW"); ps.setLong(3, krwBalance); ps.executeUpdate();
                ps.setLong(1, 1L); ps.setString(2, "USD"); ps.setLong(3, usdBalance); ps.executeUpdate();
                // user 2
                ps.setLong(1, 2L); ps.setString(2, "KRW"); ps.setLong(3, krwBalance); ps.executeUpdate();
                ps.setLong(1, 2L); ps.setString(2, "USD"); ps.setLong(3, usdBalance); ps.executeUpdate();
            }
        }
    }
}
