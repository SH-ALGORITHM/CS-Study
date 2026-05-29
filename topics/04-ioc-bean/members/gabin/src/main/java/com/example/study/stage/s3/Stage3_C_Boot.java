package com.example.study.stage.s3;

import com.example.study.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * STAGE 3-1 C: SpringApplication.run() 부팅 시간과 Bean 수 측정.
 */
@SpringBootApplication(
    scanBasePackages = {
        "com.example.study.domain",
        "com.example.study.service",
        "com.example.study.sample"
    },
    exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        RedisAutoConfiguration.class
    }
)
public class Stage3_C_Boot {

    public static void main(String[] args) {
        long start = System.nanoTime();

        try (ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_C_Boot.class, args)) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            int count = ctx.getBeanDefinitionCount();

            System.out.println("=== STAGE 3-C: SpringApplication.run ===");
            System.out.println("Bean count: " + count);
            System.out.println("elapsed: " + elapsedMs + "ms");

            MeasurementLog.save(
                "s3-1",
                "SpringApplication.run",
                "Bean " + count + "개 / " + elapsedMs + "ms"
            );
        }
    }
}
