package infra;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * 부팅 시점에 classpath:schema.sql 을 읽어서 DDL 을 실행한다.
 *
 * 3주차의 SchemaBootstrap (final class + static method) 와 비슷한 역할이지만,
 * 여기서는 Spring Bean (@Component) + @PostConstruct 로 작성해서
 * 4주차의 라이프사이클 학습 (생성자 → 의존성 주입 → @PostConstruct → 사용) 흐름과 직접 연결된다.
 *
 * TRUNCATE 까지 같이 수행해서 측정/재실행 때마다 깨끗한 상태를 보장한다.
 */
@Component
public class SchemaBootstrap {

    private static final String SCHEMA_PATH = "schema.sql";

    private final DataSource dataSource;

    public SchemaBootstrap(DataSource dataSource) {
        System.out.println("[SchemaBootstrap] constructor injection: DataSource");
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initSchema() {
        String ddl = readClasspath(SCHEMA_PATH);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(ddl);
            statement.execute("TRUNCATE notification_log RESTART IDENTITY");
            System.out.println("[SchemaBootstrap] @PostConstruct: notification_log ready (truncated)");
        } catch (SQLException e) {
            throw new IllegalStateException("notification_log 스키마 초기화 실패", e);
        }
    }

    private static String readClasspath(String path) {
        ClassLoader loader = SchemaBootstrap.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("classpath:" + path + " 가 존재하지 않음");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("schema.sql 읽기 실패", e);
        }
    }
}
