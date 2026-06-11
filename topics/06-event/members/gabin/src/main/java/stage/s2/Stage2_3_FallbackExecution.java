package stage.s2;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * STAGE 2-3 — fallbackExecution — 트랜잭션 밖에서 publishEvent 시 동작.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>NoFallback — 트랜잭션 없으면 조용히 무시 (WARN 로그조차 X) ← 함정</li>
 *   <li>WithFallback — 트랜잭션 없어도 즉시 실행</li>
 *   <li>실무 — 외부 배치처럼 트랜잭션 없는 완료 통지에서 fallback=true</li>
 * </ul>
 */
@Configuration
@EnableAutoConfiguration
public class Stage2_3_FallbackExecution {

    public record OrderCompletedEvent(Long orderId) {}

    @Bean
    public OrderCompletionNotifier orderCompletionNotifier(ApplicationEventPublisher publisher) {
        return new OrderCompletionNotifier(publisher);
    }

    @Bean public NoFallbackListener noFallback() { return new NoFallbackListener(); }
    @Bean public WithFallbackListener withFallback() { return new WithFallbackListener(); }

    public static class OrderCompletionNotifier {
        private final ApplicationEventPublisher publisher;
        public OrderCompletionNotifier(ApplicationEventPublisher publisher) { this.publisher = publisher; }

        // 외부 배치가 이미 완료된 주문을 Spring 트랜잭션 밖에서 통지하는 상황
        public void notifyCompleted(Long orderId) {
            System.out.println("  [SERVICE] 완료 통지 (트랜잭션 없음) orderId=" + orderId);
            publisher.publishEvent(new OrderCompletedEvent(orderId));
            System.out.println("  [SERVICE] return");
        }
    }

    public static class NoFallbackListener {
        // 기본 fallbackExecution = false
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void on(OrderCompletedEvent e) {
            System.out.println("    [NoFallback] orderId=" + e.orderId() + " ← 트랜잭션 안이라면 호출");
        }
    }

    public static class WithFallbackListener {
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT,
                                    fallbackExecution = true)
        public void on(OrderCompletedEvent e) {
            System.out.println("    [WithFallback] orderId=" + e.orderId() + " ← 트랜잭션 없어도 호출됨");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_3_FallbackExecution.class, args);

        MeasurementLog.title("STAGE 2-3 — fallbackExecution (트랜잭션 밖 publishEvent)");

        ctx.getBean(OrderCompletionNotifier.class).notifyCompleted(42L);

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · NoFallback (기본) — 호출 안 됨. WARN 도 안 나옴 → 함정");
        System.out.println("  · WithFallback (true) — 즉시 실행. 그냥 @EventListener 처럼");
        System.out.println("  · 트랜잭션 안 / 밖 모두 발행되는 메서드는 fallback=true 필수");
        MeasurementLog.record(
            "s2-3",
            "트랜잭션 밖 발행 — fallback=false 무시 / fallback=true 즉시 실행"
        );
        ctx.close();
    }
}

