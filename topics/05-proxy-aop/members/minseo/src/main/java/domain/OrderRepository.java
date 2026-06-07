package domain;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * JDBC Repository — STAGE 2-1 ThreadLocal 검증의 핵심.
 */
@Repository
public class OrderRepository {

    private final DataSource dataSource;

    /** 토글로 Step 1 (함정) vs Step 3 (해결) 비교. */
    public static volatile boolean useNaiveMode = true;

    public OrderRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public BigDecimal getBalance(long id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT balance FROM account WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal(1);
                throw new IllegalStateException("no account id=" + id);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void minusBalance(long id, BigDecimal amount) {
        updateBalance(id, amount.negate());
    }

    public void plusBalance(long id, BigDecimal amount) {
        updateBalance(id, amount);
    }

    private void updateBalance(long id, BigDecimal delta) {
        Connection conn = null;
        boolean borrowed = false;
        try {
            if (useNaiveMode) {
                // Step 1 — 매번 새 conn. Aspect 의 트랜잭션과 무관 → 함정
                conn = dataSource.getConnection();
                borrowed = true;
                System.out.println("[Repo] 새 Connection 사용 (함정) - Conn: " + conn);
            } else {
                // Step 3 — ThreadLocal 의 conn. Aspect 와 같은 트랜잭션
                conn = MyTransactionalAspect.currentConnection(dataSource);
                borrowed = !MyTransactionalAspect.isTransactionActive();
                System.out.println("[Repo] 트랜잭션 Connection 사용 (해결) - Conn: " + conn);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE account SET balance = balance + ? WHERE id = ?")) {
                ps.setBigDecimal(1, delta);
                ps.setLong(2, id);
                int updated = ps.executeUpdate();
                if (updated == 0) throw new IllegalStateException("no account id=" + id);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (borrowed && conn != null) {
                try { conn.close(); } catch (SQLException ignore) {}
            }
        }
    }
}
