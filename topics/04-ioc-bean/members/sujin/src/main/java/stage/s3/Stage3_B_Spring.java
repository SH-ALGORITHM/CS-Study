package stage.s3;

import domain.EmailSender;
import domain.NotificationLogRepository;
import domain.NotificationService;
import domain.PushSender;
import domain.SlackSender;
import domain.SmsSender;
import infra.DataSourceConfig;
import java.util.Arrays;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * STAGE 3-1 B. 순수 Spring (AnnotationConfigApplicationContext, Spring Boot X).
 *
 * Spring 컨테이너만 띄움 — 자동 설정 (AutoConfiguration) 없음.
 * 부팅 시간 + 빈 Config 와 도메인 포함 Config 두 케이스의 Bean 수 비교.
 *
 * NOTE: SchemaBootstrap 은 @PostConstruct 에서 실제 DB 연결 → 측정 환경 의존성 제거하려고
 *   여기선 DataSourceConfig + 4 sender + NotificationLogRepository + NotificationService 만 직접 등록.
 */
public class Stage3_B_Spring {

    private static final int ITERATIONS = 5;

    @Configuration
    static class EmptyConfig {
    }

    @Configuration
    @Import(DataSourceConfig.class)
    static class NotificationDomainConfig {
        // @Bean 으로 직접 등록 — @ComponentScan 안 켜고 명시적으로 묶기
        // (스캔 켜면 SchemaBootstrap 까지 잡혀서 측정에 DB 의존 끼어듦)
    }

    public static void main(String[] args) {
        System.out.println("=== STAGE 3-1 B. 순수 Spring (AnnotationConfigApplicationContext) ===");

        // ── 부팅 시간 측정 ──
        long[] elapsedNs = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            elapsedNs[i] = measureBootOnce();
            System.out.printf("[%d] AnnotationConfig 부팅 = %.3f ms%n", i + 1, elapsedNs[i] / 1_000_000.0);
        }
        System.out.printf("워밍업 1회 제외 4회 평균 = %.3f ms%n", warmAverageMs(elapsedNs));

        // ── Bean 수 측정 ──
        System.out.println();
        System.out.println("--- Bean 수 비교 ---");

        try (var emptyCtx = new AnnotationConfigApplicationContext(EmptyConfig.class)) {
            System.out.println("Empty @Configuration              : " + emptyCtx.getBeanDefinitionCount() + " beans");
            printNamesContaining(emptyCtx);
        }

        try (var domainCtx = newDomainContext()) {
            System.out.println("Notification 도메인 추가 후         : " + domainCtx.getBeanDefinitionCount() + " beans");
            printNamesContaining(domainCtx);
        }

        System.out.println();
        System.out.println("Empty 케이스의 Bean 들은 모두 Spring 인프라 (configurationAnnotationProcessor 등).");
        System.out.println("도메인 케이스 증가분은 본인이 등록한 Bean 수 + DataSourceConfig 의 @Bean (dataSource) 등.");
    }

    private static long measureBootOnce() {
        long start = System.nanoTime();
        try (var ctx = newDomainContext()) {
            // ctx 자체가 생성된 시점까지가 부팅 시간
            long elapsed = System.nanoTime() - start;
            return elapsed;
        }
    }

    private static AnnotationConfigApplicationContext newDomainContext() {
        var ctx = new AnnotationConfigApplicationContext();
        ctx.register(
            NotificationDomainConfig.class,
            EmailSender.class,
            SmsSender.class,
            PushSender.class,
            SlackSender.class,
            NotificationLogRepository.class,
            NotificationService.class
        );
        ctx.refresh();
        return ctx;
    }

    private static double warmAverageMs(long[] elapsedNs) {
        long sumNs = 0;
        for (int i = 1; i < elapsedNs.length; i++) {
            sumNs += elapsedNs[i];
        }
        return sumNs / (double) (elapsedNs.length - 1) / 1_000_000.0;
    }

    private static void printNamesContaining(AnnotationConfigApplicationContext ctx) {
        Arrays.stream(ctx.getBeanDefinitionNames())
            .filter(name -> name.equals("email")
                || name.equals("smsSender")
                || name.equals("push")
                || name.equals("slackSender")
                || name.equals("notificationService")
                || name.equals("notificationLogRepository")
                || name.equals("dataSource"))
            .sorted()
            .forEach(name -> System.out.println("    - " + name));
    }
}
