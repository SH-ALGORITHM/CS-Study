package infra;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

/**
 * PostgreSQL 연결용 HikariCP DataSource를 생성하는 팩토리.
 *
 * STAGE 2 이후에는 여러 스레드가 동시에 DB 작업을 수행하므로
 * 매번 새 connection을 만들지 않고 connection pool에서 빌려 쓴다.
 *
 * HikariCP의 autoCommit 기본값은 true다.
 * 트랜잭션 안에서 락을 유지하려면 작업 메서드에서 반드시
 * conn.setAutoCommit(false)를 호출해야 한다.
 *
 * poolSize가 동시 스레드 수보다 너무 작으면 DB lock wait가 아니라
 * connection pool 대기 시간이 측정에 섞일 수 있다.
 */
public final class DataSourceFactory {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5433/csstudy";
    private static final String USERNAME = "csstudy";
    private static final String PASSWORD = "csstudy1234";

    private DataSourceFactory() {
    }

    /**
     * 지정한 pool size로 DataSource를 생성한다.
     *
     * 측정 시에는 스레드 수와 pool size의 차이가 결과에 영향을 줄 수 있으므로
     * 실험 조건에 맞춰 명시적으로 지정한다.
     */
    public static DataSource create(int poolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(JDBC_URL);
        config.setUsername(USERNAME);
        config.setPassword(PASSWORD);
        config.setMaximumPoolSize(poolSize);
        config.setAutoCommit(true);
        config.setPoolName("sujin-w03-stock-trade");
        return new HikariDataSource(config);
    }

    /**
     * HikariCP connection pool을 종료한다.
     *
     * main 메서드 종료 시 호출해 pool thread와 DB connection을 정리한다.
     */
    public static void close(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            hikariDataSource.close();
        }
    }
}
