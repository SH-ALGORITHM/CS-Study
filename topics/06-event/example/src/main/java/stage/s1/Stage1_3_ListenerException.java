package stage.s1;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

/**
 * STAGE 1-3 — 한 리스너에서 예외 던지면 다음 리스너는?
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>동기 + 트랜잭션 무관 케이스 — 다음 리스너 (L3) 호출 안 됨</li>
 *   <li>예외가 publisher 까지 그대로 전파 → [publisher] return 출력 X</li>
 *   <li>STAGE 2 의 @TransactionalEventListener / STAGE 3 의 @Async 는 동작 다름 (각각 격리)</li>
 * </ul>
 */
@Configuration
@EnableAutoConfiguration
public class Stage1_3_ListenerException {

    public record Event(String payload) {}

    @Bean
    public Publisher publisher(ApplicationEventPublisher p) {
        return new Publisher(p);
    }

    @Bean public L1 l1() { return new L1(); }
    @Bean public L2Failing l2() { return new L2Failing(); }
    @Bean public L3 l3() { return new L3(); }

    public static class Publisher {
        private final ApplicationEventPublisher publisher;
        public Publisher(ApplicationEventPublisher publisher) { this.publisher = publisher; }
        public void publish(String s) {
            System.out.println("[publisher] publish — " + s);
            try {
                publisher.publishEvent(new Event(s));
                System.out.println("[publisher] return (정상)");
            } catch (RuntimeException ex) {
                System.out.println("[publisher] caught — " + ex.getMessage());
            }
        }
    }

    public static class L1 {
        @EventListener
        @Order(1)
        public void on(Event e) { System.out.println("  [L1] " + e.payload()); }
    }

    public static class L2Failing {
        @EventListener
        @Order(2)
        public void on(Event e) {
            System.out.println("  [L2] received — throwing");
            throw new RuntimeException("일부러 실패");
        }
    }

    public static class L3 {
        @EventListener
        @Order(3)
        public void on(Event e) { System.out.println("  [L3] " + e.payload()); }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage1_3_ListenerException.class, args);
        MeasurementLog.title("STAGE 1-3 — 리스너 예외 — 다음 리스너 호출 안 됨");
        ctx.getBean(Publisher.class).publish("hello");

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · 동기 + 트랜잭션 무관 — L2 예외로 L3 호출 안 됨 + publisher 에 전파");
        System.out.println("  · @TransactionalEventListener — 각 phase 별로 다른 격리");
        System.out.println("  · @Async — 예외가 publisher 에 전파 안 됨 (AsyncUncaughtExceptionHandler 자리)");
        ctx.close();
    }
}
