package domain;

import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * Repository는 DataSource를 주입받아 필요할 때 DB Connection을 얻는다.
 *
 * HikariDataSource 같은 구체 클래스가 아니라 DataSource 인터페이스에 의존하므로,
 * 나중에 다른 connection pool로 바뀌어도 Repository 코드 변경을 줄일 수 있다.
 */
@Repository
public class NotificationLogRepository {

    private final DataSource dataSource;

    public NotificationLogRepository(DataSource dataSource) {
        System.out.println("[NotificationLogRepository] constructor injection: DataSource");
        this.dataSource = dataSource;
    }

    public String dataSourceType() {
        return dataSource.getClass().getSimpleName();
    }
}
