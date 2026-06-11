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
 * STAGE 1-2 — 리스너 여러 개 + @Order 로 호출 순서 명시.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>@Order 숫자 작은 게 먼저 호출 (5 주차 @Order 와 같은 규칙)</li>
 *   <li>@Order 없으면 임의 순서 (불확정)</li>
 *   <li>모든 리스너가 publisher 와 같은 스레드 — 동기</li>
 * </ul>
 */
@Configuration
@EnableAutoConfiguration
public class Stage1_2_MultipleListeners {

    public record GreetEvent(String name) {}

    @Bean
    public GreetService greetService(ApplicationEventPublisher publisher) {
        return new GreetService(publisher);
    }

    @Bean public Listener1 l1() { return new Listener1(); }
    @Bean public Listener2 l2() { return new Listener2(); }
    @Bean public Listener3 l3() { return new Listener3(); }

    public static class GreetService {
        private final ApplicationEventPublisher publisher;
        public GreetService(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }
        public void greet(String name) {
            System.out.println("[publisher] greet — " + name);
            publisher.publishEvent(new GreetEvent(name));
            System.out.println("[publisher] return");
        }
    }

    public static class Listener1 {
        @EventListener
        @Order(1)
        public void on(GreetEvent e) {
            System.out.println("  [L1 order=1] " + e.name());
        }
    }

    public static class Listener2 {
        @EventListener
        @Order(2)
        public void on(GreetEvent e) {
            System.out.println("  [L2 order=2] " + e.name());
        }
    }

    public static class Listener3 {
        @EventListener
        @Order(3)
        public void on(GreetEvent e) {
            System.out.println("  [L3 order=3] " + e.name());
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage1_2_MultipleListeners.class, args);
        MeasurementLog.title("STAGE 1-2 — 리스너 3 개 + @Order");
        ctx.getBean(GreetService.class).greet("world");

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · @Order 숫자 작은 게 먼저 (1 → 2 → 3)");
        System.out.println("  · @Order 없으면 임의 순서 — 면접 / 실무에서는 명시 권장");
        MeasurementLog.record("s1-2", "리스너 3개 호출 순서 확인 — @Order 1 → 2 → 3");
        ctx.close();
    }
}
