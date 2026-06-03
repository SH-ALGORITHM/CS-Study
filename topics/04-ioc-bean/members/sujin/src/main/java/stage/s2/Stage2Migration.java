package stage.s2;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import domain.NotificationLogRepository;
import infra.DataSourceConfig;
import infra.RedisConfig;
import io.lettuce.core.RedisClient;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

public class Stage2Migration {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5433/csstudy";
    private static final String USERNAME = "csstudy";
    private static final String PASSWORD = "csstudy1234";

    public static final class OldDataSourceFactory {

        private OldDataSourceFactory() {
        }

        public static DataSource create(int poolSize) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(JDBC_URL);
            config.setUsername(USERNAME);
            config.setPassword(PASSWORD);
            config.setMaximumPoolSize(poolSize);
            config.setAutoCommit(true);
            config.setPoolName("sujin-w03-stock-trade");
            config.setInitializationFailTimeout(-1);
            return new HikariDataSource(config);
        }

        public static void close(DataSource dataSource) {
            if (dataSource instanceof HikariDataSource hikariDataSource) {
                hikariDataSource.close();
            }
        }
    }

    public static final class OldRedisClientFactory {

        private static final RedisClient CLIENT = RedisClient.create("redis://localhost:6379");

        private OldRedisClientFactory() {
        }

        public static RedisClient get() {
            return CLIENT;
        }

        public static void shutdown() {
            CLIENT.shutdown();
        }
    }

    @Configuration
    @Import({DataSourceConfig.class, RedisConfig.class})
    @ComponentScan(basePackageClasses = NotificationLogRepository.class)
    static class MigrationConfig {
    }

    public static void main(String[] args) {
        System.out.println("=== STAGE 2-1. Before: 3주차 직접 팩토리 ===");
        DataSource oldDataSource = OldDataSourceFactory.create(10);
        RedisClient oldRedisClient = OldRedisClientFactory.get();
        System.out.println("old DataSource type = " + oldDataSource.getClass().getSimpleName());
        System.out.println("old RedisClient type = " + oldRedisClient.getClass().getSimpleName());
        tryConnection(oldDataSource);
        OldDataSourceFactory.close(oldDataSource);
        OldRedisClientFactory.shutdown();
        System.out.println("old factory cleanup = 직접 close()/shutdown() 호출");

        System.out.println();
        System.out.println("=== STAGE 2-1. After: Spring @Bean ===");
        var context = new AnnotationConfigApplicationContext(MigrationConfig.class);
        DataSource springDataSource = context.getBean(DataSource.class);
        RedisClient springRedisClient = context.getBean(RedisClient.class);
        NotificationLogRepository repository = context.getBean(NotificationLogRepository.class);

        System.out.println("spring DataSource type = " + springDataSource.getClass().getSimpleName());
        System.out.println("spring RedisClient type = " + springRedisClient.getClass().getSimpleName());
        System.out.println("repository injected DataSource type = " + repository.dataSourceType());
        tryConnection(springDataSource);

        context.close();
        System.out.println("spring cleanup = context.close()가 destroyMethod를 자동 호출");
    }

    private static void tryConnection(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            System.out.println("DB connection = success, catalog=" + connection.getCatalog());
        } catch (SQLException e) {
            System.out.println("DB connection = skipped/failed: " + e.getMessage());
            System.out.println("PostgreSQL 확인이 필요하면 docker compose up -d 후 다시 실행");
        }
    }
}
