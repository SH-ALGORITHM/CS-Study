package domain;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

    public static volatile boolean useNaiveMode = true;

    private final DataSource dataSource;

    public OrderRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public BigDecimal getBalance(long id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT balance FROM account WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1);
                }
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
                conn = dataSource.getConnection();
                borrowed = true;
            } else {
                conn = MyTransactionalAspect.currentConnection(dataSource);
                borrowed = !MyTransactionalAspect.isTransactionActive();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE account SET balance = balance + ? WHERE id = ?")) {
                ps.setBigDecimal(1, delta);
                ps.setLong(2, id);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new IllegalStateException("no account id=" + id);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (borrowed && conn != null) {
                try {
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }
}
