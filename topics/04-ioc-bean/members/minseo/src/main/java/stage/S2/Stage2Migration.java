package stage.S2;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import domain.AuthRepository;
import infra.DataSourceConfig;
import infra.MeasurementLog;
import infra.RedisConfig;
import io.lettuce.core.RedisClient;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * STAGE 2-1: 3주차 수동 팩토리 -> 스프링 IoC 마이그레이션 비교
 */
public class Stage2Migration {

    // --- [Before] 3주차 방식 (직접 팩토리 관리) ---
    public static final class OldDataSourceFactory {
        public static DataSource create(int poolSize) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:postgresql://localhost:5433/csstudy");
            config.setUsername("csstudy");
            config.setPassword("csstudy1234");
            config.setMaximumPoolSize(poolSize);
            config.setPoolName("minseo-w03-ticket-old");
            return new HikariDataSource(config);
        }

        public static void close(DataSource dataSource) {
            if (dataSource instanceof HikariDataSource h) h.close();
        }
    }

    public static final class OldRedisClientFactory {
        private static final RedisClient CLIENT = RedisClient.create("redis://localhost:6379");
        public static RedisClient get() { return CLIENT; }
        public static void shutdown() { CLIENT.shutdown(); }
    }

    // --- [After] 스프링 설정 (마이그레이션) ---
    @Configuration
    @Import({DataSourceConfig.class, RedisConfig.class}) // 분리된 설정 파일들 가져오기
    @ComponentScan(basePackages = "domain") // Repository 자동 스캔
    static class MigrationConfig {
    }

    public static void main(String[] args) {
        System.out.println("=== STAGE 2-1. Before: 3주차 직접 팩토리 ===");
        DataSource oldDs = OldDataSourceFactory.create(10);
        RedisClient oldRedis = OldRedisClientFactory.get();

        System.out.println("old DataSource type = " + oldDs.getClass().getSimpleName());
        tryConnection(oldDs);

        OldDataSourceFactory.close(oldDs);
        OldRedisClientFactory.shutdown();
        System.out.println("old factory cleanup = 직접 close()/shutdown() 호출 완료");

        System.out.println("\n=== STAGE 2-1. After: Spring IoC 컨테이너 ===");
        var ctx = new AnnotationConfigApplicationContext(MigrationConfig.class);

        DataSource springDs = ctx.getBean(DataSource.class);
        RedisClient springRedis = ctx.getBean(RedisClient.class);
        AuthRepository repository = ctx.getBean(AuthRepository.class);

        System.out.println("spring DataSource type = " + springDs.getClass().getSimpleName());
        System.out.println("spring RedisClient type = " + springRedis.getClass().getSimpleName());
        System.out.println("repository injected DataSource type = " + repository.dataSourceType());

        tryConnection(springDs);

        ctx.close();
        System.out.println("spring cleanup = context.close()가 destroyMethod를 자동 호출 완료");

        MeasurementLog.save("s2-1", "3주차 코드 마이그레이션 (Before vs After)",
            "직접 팩토리 관리 방식에서 스프링 @Configuration & @Bean 방식으로 전환 완료");
    }

    private static void tryConnection(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            System.out.println("DB connection = success, catalog=" + conn.getCatalog());
        } catch (SQLException e) {
            System.out.println("DB connection = skipped/failed (Docker 확인 필요): " + e.getMessage());
        }
    }
}
