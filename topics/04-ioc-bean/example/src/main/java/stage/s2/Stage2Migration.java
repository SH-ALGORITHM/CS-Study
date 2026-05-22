package stage.s2;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * STAGE 2-1: 3주차 DataSourceFactory (직접 싱글톤) → 4주차 @Configuration + @Bean.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>Before: static 초기화 + 본인이 shutdown() 명시 호출</li>
 *   <li>After: @Bean(destroyMethod="close") + ctx.close() 가 자동 호출</li>
 *   <li>코드 라인 수 비교 + 다른 Bean 에 DataSource 주입 가능해짐</li>
 * </ul>
 *
 * <h3>실행</h3>
 * <pre>
 * docker compose up -d   # PostgreSQL 필요
 * ./gradlew run -PmainClass=stage.Stage2Migration
 * </pre>
 */
public class Stage2Migration {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5433/csstudy";
    private static final String USER = "csstudy";
    private static final String PASSWORD = "csstudy1234";

    // ============ Before: 3주차 패턴 (직접 싱글톤) ============
    public static final class OldDataSourceFactory {
        private static final HikariDataSource DS;
        static {
            DS = new HikariDataSource();
            DS.setJdbcUrl(JDBC_URL);
            DS.setUsername(USER);
            DS.setPassword(PASSWORD);
        }
        public static DataSource get() { return DS; }
        public static void shutdown() { DS.close(); }
    }

    // ============ After: 4주차 패턴 (@Bean) ============
    @Configuration
    static class NewDataSourceConfig {
        @Bean(destroyMethod = "close")
        public HikariDataSource dataSource() {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(JDBC_URL);
            ds.setUsername(USER);
            ds.setPassword(PASSWORD);
            return ds;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Before: 직접 싱글톤 (3주차) ===");
        try {
            DataSource oldDs = OldDataSourceFactory.get();
            try (Connection c = oldDs.getConnection()) {
                System.out.println("  연결 성공: " + c.getCatalog());
            }
            OldDataSourceFactory.shutdown();   // 본인이 명시 호출
            System.out.println("  shutdown() 직접 호출");
        } catch (SQLException e) {
            System.out.println("  연결 실패: " + e.getMessage());
            System.out.println("  → docker compose up -d 후 다시 시도");
        }

        System.out.println("\n=== After: @Bean(destroyMethod=\"close\") (4주차) ===");
        try {
            var ctx = new AnnotationConfigApplicationContext(NewDataSourceConfig.class);
            DataSource newDs = ctx.getBean(DataSource.class);
            try (Connection c = newDs.getConnection()) {
                System.out.println("  연결 성공: " + c.getCatalog());
            }
            ctx.close();   // close() 가 알아서 호출됨
            System.out.println("  ctx.close() 가 destroyMethod=\"close\" 자동 호출");
        } catch (SQLException e) {
            System.out.println("  연결 실패: " + e.getMessage());
        }

        System.out.println("\n[학습 포인트]");
        System.out.println("  - shutdown() 명시 호출이 사라짐 — 컨테이너가 destroyMethod 호출");
        System.out.println("  - static 초기화 → @Configuration 클래스 (테스트 시 mock 가능)");
        System.out.println("  - DataSource 가 다른 Bean 에 주입 가능 — 3주차에는 OldDataSourceFactory.get() 호출이 코드 곳곳에");
    }
}
