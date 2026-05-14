package infra;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DataSourceFactory {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5433/csstudy";
    private static final String USERNAME = "csstudy";
    private static final String PASSWORD = "csstudy1234";

    private DataSourceFactory() {
    }

    public static DataSource create(int poolSize) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(JDBC_URL);
        cfg.setUsername(USERNAME);
        cfg.setPassword(PASSWORD);
        cfg.setMaximumPoolSize(poolSize);
        cfg.setAutoCommit(true);
        cfg.setPoolName("csstudy-w02");
        return new HikariDataSource(cfg);
    }

    public static void close(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource h) {
            h.close();

        }
    }
}
