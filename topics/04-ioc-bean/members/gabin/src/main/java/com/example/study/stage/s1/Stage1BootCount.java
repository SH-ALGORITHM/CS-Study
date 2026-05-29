package com.example.study.stage.s1;

import com.example.study.MeasurementLog;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * STAGE 1-4: @SpringBootApplication이 자동 등록하는 Bean 수 확인.
 */
@SpringBootApplication(
    scanBasePackages = "com.example.study",
    exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        RedisAutoConfiguration.class
    }
)
public class Stage1BootCount {

    public static void main(String[] args) {
        long start = System.nanoTime();

        try (ConfigurableApplicationContext ctx = SpringApplication.run(Stage1BootCount.class, args)) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            int count = ctx.getBeanDefinitionCount();
            String[] names = ctx.getBeanDefinitionNames();

            System.out.println("=== @SpringBootApplication Bean count ===");
            System.out.println("총 Bean 수: " + count);
            System.out.println("부팅 시간: " + elapsedMs + "ms");

            System.out.println();
            System.out.println("=== 등록된 Bean 이름 일부 ===");
            Arrays.stream(names)
                .limit(30)
                .forEach(name -> System.out.println("  - " + name));

            String firstThirty = Arrays.stream(names)
                .limit(30)
                .map(name -> "  - " + name)
                .collect(Collectors.joining(System.lineSeparator()));

            MeasurementLog.save(
                "s1-4",
                "@SpringBootApplication Bean count",
                String.join(System.lineSeparator(),
                    "",
                    "  총 Bean 수: " + count,
                    "  부팅 시간: " + elapsedMs + "ms",
                    "",
                    "  처음 30개 Bean 이름",
                    firstThirty,
                    "",
                    "  관찰",
                    "  - @SpringBootApplication은 @SpringBootConfiguration, @EnableAutoConfiguration, @ComponentScan을 포함한다.",
                    "  - 내가 직접 등록하지 않은 Spring Boot 기반 Bean도 자동으로 등록된다.",
                    "  - 이번 S1-4는 DB/Redis 실습이 아니므로 DataSource/JPA/Redis 자동설정은 제외했다.",
                    "  - DB 연결이 필요한 S2 이후에는 spring.datasource 설정과 Docker DB 실행이 필요하다."
                )
            );
        }
    }
}
