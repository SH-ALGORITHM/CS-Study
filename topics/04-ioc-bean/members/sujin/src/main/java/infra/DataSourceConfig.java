package infra;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSourceConfig {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5433/csstudy";
    private static final String USERNAME = "csstudy";
    private static final String PASSWORD = "csstudy1234";

    /*
     * DataSource는 DB Connection을 얻기 위한 Java 표준 인터페이스다.
     * 여기서는 HikariDataSource(HikariCP connection pool)를 구현체로 사용한다.
     *
     * @Bean 메서드는 일반 Java 코드에서 직접 호출하지 않아도 된다.
     * AnnotationConfigApplicationContext가 이 설정 클래스를 읽을 때 dataSource()를 호출하고,
     * 반환된 객체를 Spring Bean으로 등록한다.
     *
     * 그래서 IDE에서 method usage가 없어 보여도 정상이다.
     * 실제 사용은 context.getBean(DataSource.class) 조회나
     * NotificationLogRepository 생성자 주입으로 확인할 수 있다.
     */
    @Bean(destroyMethod = "close")
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(JDBC_URL);
        config.setUsername(USERNAME);
        config.setPassword(PASSWORD);
        config.setMaximumPoolSize(10);
        config.setAutoCommit(true);
        config.setPoolName("sujin-w04-ioc-bean");
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }
}
