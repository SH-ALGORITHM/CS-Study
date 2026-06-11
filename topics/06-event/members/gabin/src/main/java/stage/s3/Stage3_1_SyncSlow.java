package stage.s3;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * STAGE 3-1 — 동기 리스너의 한계: 리스너가 느리면 publisher 도 그만큼 블록.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>리스너 3 개 × 200ms 면 publisher 총 시간 ~ 600ms</li>
 *   <li>모두 같은 스레드 (main) — 동기 분배</li>
 *   <li>HTTP 요청 처리 중이면 응답이 그만큼 늦어짐 → 비동기 필요</li>
 * </ul>
 *
 * <h3>해결 → {@link Stage3_2_AsyncFast}</h3>
 */
@Configuration
@EnableAutoConfiguration
public class Stage3_1_SyncSlow {

    public record OrderCompletedEvent(Long orderId) {}

    @Bean
    public OrderService orderService(ApplicationEventPublisher publisher) {
        return new OrderService(publisher);
    }

    @Bean public SlowListener1 l1() { return new SlowListener1(); }
    @Bean public SlowListener2 l2() { return new SlowListener2(); }
    @Bean public SlowListener3 l3() { return new SlowListener3(); }

    public static class OrderService {
        private final ApplicationEventPublisher publisher;
        public OrderService(ApplicationEventPublisher publisher) { this.publisher = publisher; }

        public long completeOrder(Long orderId) {
            long t1 = System.nanoTime();
            publisher.publishEvent(new OrderCompletedEvent(orderId));
            long elapsedMs = (System.nanoTime() - t1) / 1_000_000;
            System.out.println("  [publisher] return — total=" + elapsedMs + "ms thread=" + MeasurementLog.thread());
            return elapsedMs;
        }
    }

    private static void slowWork(String label) throws InterruptedException {
        System.out.println("    [" + label + "] start thread=" + MeasurementLog.thread());
        Thread.sleep(200);
        System.out.println("    [" + label + "] end");
    }

    public static class SlowListener1 {
        @EventListener
        public void on(OrderCompletedEvent e) throws InterruptedException { slowWork("재고 확정"); }
    }

    public static class SlowListener2 {
        @EventListener
        public void on(OrderCompletedEvent e) throws InterruptedException { slowWork("고객 알림"); }
    }

    public static class SlowListener3 {
        @EventListener
        public void on(OrderCompletedEvent e) throws InterruptedException { slowWork("통계 집계"); }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_1_SyncSlow.class, args);

        MeasurementLog.title("STAGE 3-1 — 동기 리스너 3 개 × 200ms");
        long elapsedMs = ctx.getBean(OrderService.class).completeOrder(1L);
        MeasurementLog.record(
            "s3-1",
            "동기 리스너 3개 x 200ms — publisher=" + elapsedMs + "ms / thread=main"
        );

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · 모두 같은 스레드 (main) — 순차 실행 → 총 600ms");
        System.out.println("  · publisher 가 그만큼 블록 → HTTP 응답 지연");
        System.out.println("  · 해결 → Stage3_2_AsyncFast");
        ctx.close();
    }
}

