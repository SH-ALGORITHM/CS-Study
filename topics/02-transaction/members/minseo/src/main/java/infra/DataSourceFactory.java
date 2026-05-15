package infra;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

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
        cfg.setPoolName("csstudy-minseo-w02");
        return new HikariDataSource(cfg);
    }

    public static void close(DataSource ds) {
        if (ds instanceof HikariDataSource h) {
            h.close();
        }
    }
}
