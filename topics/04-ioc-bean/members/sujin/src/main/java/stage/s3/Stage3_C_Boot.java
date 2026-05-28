package stage.s3;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

/**
 * STAGE 3-1 C. Spring Boot (SpringApplication.run).
 *
 * @SpringBootApplication 의 @EnableAutoConfiguration 이 켜진 상태.
 * AnnotationConfigApplicationContext (3-1 B) 보다 자동 설정 묶음만큼 더 느리고, Bean 수도 훨씬 많다.
 *
 * 측정 안정화:
 *  - 웹 서버 안 띄움 (WebApplicationType.NONE)
 *  - DB / JPA / Redis 자동 설정 exclude (Stage2Layering 과 같은 정책)
 *  - DevTools restart / LiveReload 비활성화
 *  - banner / log-startup-info OFF
 *
 * NotificationDomainBootApplication 의 scanBasePackages 는 "domain" 만.
 * "infra" 까지 잡으면 SchemaBootstrap 의 @PostConstruct 에서 DDL 실행 → DB 필요.
 */
public class Stage3_C_Boot {

    private static final int ITERATIONS = 5;

    @SpringBootApplication(
        scanBasePackages = "domain",
        exclude = {
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class
        }
    )
    @ComponentScan(basePackages = "domain")
    static class NotificationDomainBootApplication {
    }

    public static void main(String[] args) {
        System.setProperty("spring.devtools.restart.enabled", "false");
        System.setProperty("spring.devtools.livereload.enabled", "false");
        System.setProperty("spring.devtools.add-properties", "false");

        System.out.println("=== STAGE 3-1 C. Spring Boot (SpringApplication.run) ===");

        // ── 부팅 시간 측정 ──
        long[] elapsedNs = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            elapsedNs[i] = measureBootOnce();
            System.out.printf("[%d] SpringApplication.run 부팅 = %.3f ms%n", i + 1, elapsedNs[i] / 1_000_000.0);
        }
        System.out.printf("워밍업 1회 제외 4회 평균 = %.3f ms%n", warmAverageMs(elapsedNs));

        // ── Bean 수 측정 ──
        System.out.println();
        System.out.println("--- Bean 수 ---");
        try (ConfigurableApplicationContext ctx = newBootContext()) {
            System.out.println("@SpringBootApplication (domain 포함) : " + ctx.getBeanDefinitionCount() + " beans");
            System.out.println("도메인 Bean 만 추출:");
            Arrays.stream(ctx.getBeanDefinitionNames())
                .filter(name -> name.equals("email")
                    || name.equals("smsSender")
                    || name.equals("push")
                    || name.equals("slackSender")
                    || name.equals("notificationService")
                    || name.equals("notificationLogRepository"))
                .sorted()
                .forEach(name -> System.out.println("    - " + name));

            System.out.println("자동 설정 / 인프라 Bean 예시 (10개):");
            Arrays.stream(ctx.getBeanDefinitionNames())
                .filter(name -> name.contains("AutoConfiguration")
                    || name.contains("autoConfiguration")
                    || name.contains("applicationTaskExecutor"))
                .limit(10)
                .forEach(name -> System.out.println("    - " + name));
        }
    }

    private static long measureBootOnce() {
        long start = System.nanoTime();
        ConfigurableApplicationContext ctx = newBootContext();
        long elapsed = System.nanoTime() - start;
        ctx.close();
        return elapsed;
    }

    private static ConfigurableApplicationContext newBootContext() {
        return new SpringApplicationBuilder(NotificationDomainBootApplication.class)
            .web(WebApplicationType.NONE)
            .properties(
                "spring.main.banner-mode=off",
                "spring.main.log-startup-info=false"
            )
            .run();
    }

    private static double warmAverageMs(long[] elapsedNs) {
        long sumNs = 0;
        for (int i = 1; i < elapsedNs.length; i++) {
            sumNs += elapsedNs[i];
        }
        return sumNs / (double) (elapsedNs.length - 1) / 1_000_000.0;
    }

    @SuppressWarnings("unused")
    private static void unused() {
        // SpringApplication 사용 표시 — IDE warning 억제용 더미 참조
        SpringApplication.class.getName();
    }
}
