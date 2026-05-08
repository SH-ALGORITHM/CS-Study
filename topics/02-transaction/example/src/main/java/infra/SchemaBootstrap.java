package infra;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 매 측정 전 account 테이블을 깨끗한 시작 상태로 복원.
 *
 * <h3>왜 매번 초기화하나</h3>
 * 1주차 측정 원칙과 동일: 이전 측정의 잔여 상태가 다음 측정에 영향을 주면
 * 결과 해석이 어려워진다. {@code TRUNCATE} + {@code INSERT} 로 명시적 초기화.
 *
 * <h3>idempotent 보장</h3>
 * {@code CREATE TABLE IF NOT EXISTS} → 처음 실행 시 테이블 생성, 재실행 시 무해.
 */
public final class SchemaBootstrap {

    private SchemaBootstrap() {}

    /** STAGE 2/3 — 단일 계좌 race 측정용. id=1, balance=초기값 으로 리셋. */
    public static void resetAccount(DataSource ds, long id, long balance) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS account (id BIGINT PRIMARY KEY, balance BIGINT NOT NULL)");
            s.execute("DELETE FROM account WHERE id = " + id);
            try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO account (id, balance) VALUES (?, ?)")) {
                ps.setLong(1, id);
                ps.setLong(2, balance);
                ps.executeUpdate();
            }
        }
    }

    /** STAGE 4 — 데드락 측정용. 두 계좌 (id=1, id=2) 를 같은 잔고로 리셋. */
    public static void resetTwoAccounts(DataSource ds, long balance) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS account (id BIGINT PRIMARY KEY, balance BIGINT NOT NULL)");
            s.execute("TRUNCATE account");
            try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO account (id, balance) VALUES (?, ?), (?, ?)")) {
                ps.setLong(1, 1L); ps.setLong(2, balance);
                ps.setLong(3, 2L); ps.setLong(4, balance);
                ps.executeUpdate();
            }
        }
    }
}
