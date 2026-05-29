package com.example.study.stage.s2;

import com.example.study.MeasurementLog;
import com.example.study.config.DataSourceConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * STAGE 2-1: 직접 싱글톤 팩토리 방식에서 @Configuration + @Bean 방식으로 이동.
 */
public class Stage2Migration {

    public static void main(String[] args) {
        System.out.println("=== Before: 직접 팩토리 생성 + 직접 close ===");
        DataSource before = LegacyDataSourceFactory.create();
        System.out.println("생성된 DataSource: " + before.getClass().getSimpleName());
        LegacyDataSourceFactory.close(before);
        System.out.println("직접 close() 호출 완료");

        System.out.println();
        System.out.println("=== After: Spring @Bean(destroyMethod=\"close\") ===");
        DataSource after;
        try (AnnotationConfigApplicationContext ctx =
                 new AnnotationConfigApplicationContext(DataSourceConfig.class)) {
            after = ctx.getBean(DataSource.class);
            System.out.println("컨테이너에서 조회한 DataSource: " + after.getClass().getSimpleName());
            System.out.println("Bean 이름: dataSource");
            System.out.println("ctx.close() 시점에 destroyMethod=\"close\" 호출 예정");
        }
        System.out.println("컨테이너 종료 완료");

        MeasurementLog.save(
            "s2-1",
            "DataSourceFactory -> @Bean",
            String.join(System.lineSeparator(),
                "",
                "  Before",
                "  - LegacyDataSourceFactory.create()로 DataSource를 직접 생성했다.",
                "  - 사용이 끝나면 LegacyDataSourceFactory.close(ds)를 직접 호출해야 했다.",
                "",
                "  After",
                "  - DataSourceConfig.dataSource()를 @Bean으로 등록했다.",
                "  - DataSource 조회는 ctx.getBean(DataSource.class)로 했다.",
                "  - 컨테이너 종료 시 destroyMethod=\"close\"가 HikariDataSource.close()를 호출한다.",
                "",
                "  관찰",
                "  - 객체 생성 책임이 애플리케이션 코드에서 Spring 컨테이너로 이동했다.",
                "  - 자원 정리 책임도 직접 close 호출에서 Bean lifecycle의 destroy 단계로 이동했다.",
                "  - DB 연결 테스트가 아니라 생성/소멸 책임 이전 관찰이므로 getConnection()은 호출하지 않았다."
            )
        );
    }

    static class LegacyDataSourceFactory {
        static DataSource create() {
            HikariDataSource dataSource = new HikariDataSource();
            dataSource.setJdbcUrl("jdbc:postgresql://localhost:5433/csstudy");
            dataSource.setUsername("csstudy");
            dataSource.setPassword("csstudy1234");
            dataSource.setDriverClassName("org.postgresql.Driver");
            dataSource.setMaximumPoolSize(4);
            return dataSource;
        }

        static void close(DataSource dataSource) {
            if (dataSource instanceof HikariDataSource hikariDataSource) {
                hikariDataSource.close();
            }
        }
    }
}
