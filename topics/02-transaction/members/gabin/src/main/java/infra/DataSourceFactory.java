package infra;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

/**
 * HikariCP 커넥션 풀을 csstudy DB 에 연결하는 팩토리.
 *
 * <h3>왜 HikariCP 인가</h3>
 * 매번 새 커넥션을 만들면 TCP 핸드셰이크 + 인증으로 수십 ms 가 든다.
 * 풀에서 꺼내 쓰면 대여/반납만으로 끝나서 측정 결과가 안정된다.
 *
 * <h3>autoCommit 기본값 주의</h3>
 * HikariCP 의 {@code autoCommit} 기본값은 {@code true}. 즉 풀에서 꺼낸 커넥션은
 * 매 SQL 마다 즉시 커밋된다. STAGE 2 에서 트랜잭션을 직접 다루려면
 * {@code conn.setAutoCommit(false)} 를 직접 호출해야 한다 (안 부르면 트랜잭션 안 쓴 것과 동일).
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
        cfg.setAutoCommit(true);  // HikariCP 기본값 명시
        cfg.setPoolName("csstudy-w02");
        return new HikariDataSource(cfg);
    }

    public static void close(DataSource ds) {
        if (ds instanceof HikariDataSource h) {
            h.close();
        }
    }
}
