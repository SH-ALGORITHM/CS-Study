package app;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * @Timed self-invocation 함정 — 시리즈 일관성 차원 코드 재현
 * (5 주차 @Transactional / 6 주차 @Async / 7 주차 readOnly / 9 주차 @Cacheable / 11 주차 @PreAuthorize 회수).
 *
 * <h3>두 시나리오</h3>
 * <ol>
 *   <li>Bad — this.timedMethod() 호출 → AOP 우회 → bad_timed_method 메트릭 0</li>
 *   <li>Good — 다른 빈의 timedMethod() 호출 → AOP 거침 → good_timed_method 메트릭 등록</li>
 * </ol>
 *
 * <h3>실행</h3>
 * <pre>./gradlew run -PmainClass=app.TimedSelfInvocationDemo</pre>
 */
@SpringBootApplication
public class TimedSelfInvocationDemo {

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean public BadService badService() { return new BadService(); }
    @Bean public GoodService goodService() { return new GoodService(); }
    @Bean public GoodWrapper goodWrapper(GoodService g) { return new GoodWrapper(g); }

    public static class BadService {
        @Timed(value = "bad_timed_method", percentiles = {0.5, 0.95})
        public void timedMethod() {
            sleep(50);
        }

        public void wrapper() {
            // ★ this 호출 → AOP 우회 → @Timed 침묵
            this.timedMethod();
        }
    }

    public static class GoodService {
        @Timed(value = "good_timed_method", percentiles = {0.5, 0.95})
        public void timedMethod() {
            sleep(50);
        }
    }

    public static class GoodWrapper {
        private final GoodService good;
        public GoodWrapper(GoodService good) { this.good = good; }

        public void wrapper() {
            // ★ 다른 빈 호출 → AOP 거침 → @Timed 등록
            this.good.timedMethod();
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(TimedSelfInvocationDemo.class)
            .web(WebApplicationType.NONE).run(args);

        BadService bad = ctx.getBean(BadService.class);
        GoodWrapper good = ctx.getBean(GoodWrapper.class);
        MeterRegistry registry = ctx.getBean(MeterRegistry.class);

        System.out.println();
        System.out.println("=== @Timed self-invocation 함정 — 시리즈 회수 (5,6,7,9,11 주차) ===");

        // 둘 다 5 회씩 호출
        for (int i = 0; i < 5; i++) {
            bad.wrapper();
            good.wrapper();
        }

        // 메트릭 등록 여부 확인
        Timer badTimer = registry.find("bad_timed_method").timer();
        Timer goodTimer = registry.find("good_timed_method").timer();

        System.out.println();
        System.out.println("--- 메트릭 등록 여부 ---");
        System.out.println("  bad_timed_method  : " + (badTimer == null ? "❌ 등록 안 됨 (this 호출 = AOP 우회)"
            : "count=" + badTimer.count() + " (의도와 다름!)"));
        System.out.println("  good_timed_method : " + (goodTimer == null ? "등록 안 됨"
            : "count=" + goodTimer.count() + " ✓ (다른 빈 호출 = AOP 거침)"));

        System.out.println();
        System.out.println("[학습] 5 주차 @Transactional / 6 주차 @Async / 7 주차 readOnly /");
        System.out.println("       9 주차 @Cacheable / 11 주차 @PreAuthorize / 12 주차 @Timed 동일");
        System.out.println("       this 호출 = 원본 객체 직접 = AOP 프록시 우회");
        ctx.close();
    }
}
