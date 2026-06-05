package domain;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRepository {

    private final DataSource dataSource;

    public RoleRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insertRole(long userId, String role) {
        Connection conn = null;
        try {
            conn = MyTransactionalAspect.currentConnection(dataSource);
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO user_role(user_id, role) VALUES (?, ?)")) {
                ps.setLong(1, userId);
                ps.setString(2, role);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (conn != null) MyTransactionalAspect.releaseConnection(conn);
            } catch (SQLException ignored) {
            }
        }
    }

    public void insertLog(long userId, String role) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO grant_log(user_id, role, note) VALUES (?, ?, 'granted')")) {
            ps.setLong(1, userId);
            ps.setString(2, role);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public long countRole(long userId, String role) {                    // 트랜잭션 밖 호출 → 새 커넥션
        Connection conn = null;
        try {
            conn = MyTransactionalAspect.currentConnection(dataSource);
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM user_role WHERE user_id = ? AND role = ?")) {
                ps.setLong(1, userId);
                ps.setString(2, role);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (conn != null) MyTransactionalAspect.releaseConnection(conn);
            } catch (SQLException ignored) {}
        }
    }
}
