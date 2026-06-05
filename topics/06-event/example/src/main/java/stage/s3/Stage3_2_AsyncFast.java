package stage.s3;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * STAGE 3-2 — @EnableAsync + @Async → 별 스레드. publisher 즉시 반환.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>publisher 총 시간 — 거의 0ms (리스너 시간 무관)</li>
 *   <li>리스너 스레드 — event-1 / event-2 / event-3 (executor prefix)</li>
 *   <li>3 개 리스너가 병렬 실행</li>
 *   <li>ThreadPoolTaskExecutor 명시 등록 — Spring Boot 자동 (applicationTaskExecutor) 보다 명확</li>
 * </ul>
 *
 * <h3>Spring Boot 자동 기본값의 함정 (면접 단골)</h3>
 * Boot 2.1+ 자동 등록 기본값 — core=8 / queue=Integer.MAX_VALUE / max=Integer.MAX_VALUE.
 * <b>무한 큐 때문에 max 도달 불가</b> → 사실상 8 개 고정 + 무한 큐.
 * <p>아래 직접 설정 (core=4 / max=8 / queue=100) 이 의미 있는 이유: queue=100 으로 막아두면 진짜 부하 시 max 까지 증설이 일어남.
 */
@Configuration
@EnableAutoConfiguration
@EnableAsync
public class Stage3_2_AsyncFast {

    public record OrderPlacedEvent(Long orderId) {}

    @Bean(name = "applicationTaskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("event-");
        executor.initialize();
        return executor;
    }

    @Bean
    public OrderService orderService(ApplicationEventPublisher publisher) {
        return new OrderService(publisher);
    }

    @Bean public AsyncListener1 l1() { return new AsyncListener1(); }
    @Bean public AsyncListener2 l2() { return new AsyncListener2(); }
    @Bean public AsyncListener3 l3() { return new AsyncListener3(); }

    public static class OrderService {
        private final ApplicationEventPublisher publisher;
        public OrderService(ApplicationEventPublisher publisher) { this.publisher = publisher; }

        public void placeOrder(Long orderId) {
            long t1 = System.nanoTime();
            publisher.publishEvent(new OrderPlacedEvent(orderId));
            long elapsedMs = (System.nanoTime() - t1) / 1_000_000;
            System.out.println("  [publisher] return — total=" + elapsedMs + "ms thread=" + MeasurementLog.thread());
        }
    }

    private static void slowWork(String label) throws InterruptedException {
        System.out.println("    [" + label + "] start thread=" + MeasurementLog.thread());
        Thread.sleep(200);
        System.out.println("    [" + label + "] end");
    }

    public static class AsyncListener1 {
        @Async
        @EventListener
        public void on(OrderPlacedEvent e) throws InterruptedException { slowWork("L1"); }
    }

    public static class AsyncListener2 {
        @Async
        @EventListener
        public void on(OrderPlacedEvent e) throws InterruptedException { slowWork("L2"); }
    }

    public static class AsyncListener3 {
        @Async
        @EventListener
        public void on(OrderPlacedEvent e) throws InterruptedException { slowWork("L3"); }
    }

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_2_AsyncFast.class, args);

        MeasurementLog.title("STAGE 3-2 — @Async 비동기 리스너 3 개");
        ctx.getBean(OrderService.class).placeOrder(1L);

        Thread.sleep(500);      // 비동기 리스너 완료 대기

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · publisher 즉시 반환 (~ 0ms) — 리스너 시간 무관");
        System.out.println("  · 리스너는 별 스레드 (event-1, event-2, event-3) 에서 병렬");
        System.out.println("  · ThreadPoolTaskExecutor 명시 등록 — corePoolSize/maxPoolSize/queueCapacity 직접 결정");
        ctx.close();
    }
}
