package infra;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean(destroyMethod = "close")
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5433/csstudy");
        config.setUsername("csstudy");
        config.setPassword("csstudy1234");
        config.setMaximumPoolSize(10);
        config.setAutoCommit(true);
        config.setPoolName("minseo-w04-auth");
        // 부팅 시 연결 실패해도 프로세스가 바로 죽지 않도록 설정 (선택)
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }
}
