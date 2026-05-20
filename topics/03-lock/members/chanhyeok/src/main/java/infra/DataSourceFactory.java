package infra;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

/**
 * HikariCP 커넥션 풀을 csstudy DB 에 연결하는 팩토리.
 *
 * <h3>autoCommit 기본값 주의</h3>
 * HikariCP 의 {@code autoCommit} 기본값은 {@code true}.
 * 트랜잭션을 직접 다루려면 {@code conn.setAutoCommit(false)} 호출 필수.
 *
 * <h3>3 주차에서 추가로 알아야 할 것</h3>
 * 락 학습 시 풀 크기 = 동시 스레드 수 매칭 — 2 주차 교훈.
 * 풀 부족하면 lock wait 가 아니라 connection wait 가 되어 측정 노이즈.
 */
public final class DataSourceFactory {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5433/csstudy";
    private static final String USERNAME = "csstudy";
    private static final String PASSWORD = "csstudy1234";

    private DataSourceFactory() {}

    public static DataSource create(int poolSize) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(JDBC_URL);
        cfg.setUsername(USERNAME);
        cfg.setPassword(PASSWORD);
        cfg.setMaximumPoolSize(poolSize);
        cfg.setAutoCommit(true);
        cfg.setPoolName("csstudy-w03");
        return new HikariDataSource(cfg);
    }

    public static void close(DataSource ds) {
        if (ds instanceof HikariDataSource h) {
            h.close();
        }
    }
}
