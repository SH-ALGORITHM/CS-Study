package domain;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * Repository 는 DataSource 만 의존하고, 비즈니스 로직 (어디로 보낼지 등) 은 모른다.
 * Service 가 sender.send() 까지 끝낸 뒤 결과를 NotificationLog 로 만들어 save() 한다.
 *
 * HikariDataSource 같은 구체 클래스가 아니라 DataSource 인터페이스에 의존하므로
 * connection pool 구현이 바뀌어도 Repository 코드는 영향 없음.
 */
@Repository
public class NotificationLogRepository {

    private final DataSource dataSource;

    public NotificationLogRepository(DataSource dataSource) {
        System.out.println("[NotificationLogRepository] constructor injection: DataSource");
        this.dataSource = dataSource;
    }

    /**
     * notification_log 테이블에 한 row INSERT.
     * RETURN_GENERATED_KEYS 로 받은 id 를 입력 record 에 채워 반환한다.
     */
    public NotificationLog save(NotificationLog log) {
        String sql = """
            INSERT INTO notification_log (to_address, message, channel, sent_at)
            VALUES (?, ?, ?, ?)
            """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, log.to());
            statement.setString(2, log.message());
            statement.setString(3, log.channel());
            statement.setTimestamp(4, Timestamp.valueOf(log.sentAt()));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("generated key 없음 — notification_log INSERT 실패");
                }
                return log.withId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("notification_log INSERT 실패", e);
        }
    }

    public long count() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM notification_log")) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new IllegalStateException("notification_log COUNT 실패", e);
        }
    }

    public String dataSourceType() {
        return dataSource.getClass().getSimpleName();
    }
}
