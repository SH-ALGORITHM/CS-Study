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
 * (재고 도메인 + example 정석 구조)
 */
@Configuration
@EnableAutoConfiguration
public class Stage2_3_FallbackExecution {

    public record InventoryQueryEvent(Long id) {}

    @Bean
    public InventoryQueryService inventoryQueryService(ApplicationEventPublisher publisher) {
        return new InventoryQueryService(publisher);
    }

    @Bean public NoFallbackListener noFallback() { return new NoFallbackListener(); }
    @Bean public WithFallbackListener withFallback() { return new WithFallbackListener(); }

    public static class InventoryQueryService {
        private final ApplicationEventPublisher publisher;
        public InventoryQueryService(ApplicationEventPublisher publisher) { this.publisher = publisher; }

        // ★ @Transactional 없음 — 단순 조회나 트랜잭션이 없는 상황
        public void queryStock(Long id) {
            System.out.println("  [SERVICE] 재고 조회 (트랜잭션 없음) id=" + id);
            publisher.publishEvent(new InventoryQueryEvent(id));
            System.out.println("  [SERVICE] return");
        }
    }

    public static class NoFallbackListener {
        // 기본 fallbackExecution = false
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void on(InventoryQueryEvent e) {
            System.out.println("    [NoFallback] id=" + e.id() + " ← 호출 안 됨 (트랜잭션이 없으므로)");
        }
    }

    public static class WithFallbackListener {
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
        public void on(InventoryQueryEvent e) {
            System.out.println("    [WithFallback] id=" + e.id() + " ✅ 트랜잭션 없어도 호출됨");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_3_FallbackExecution.class, args);

        MeasurementLog.title("STAGE 2-3 — fallbackExecution (트랜잭션 밖에서 발행)");

        ctx.getBean(InventoryQueryService.class).queryStock(1L);

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · @TransactionalEventListener 는 기본적으로 트랜잭션이 있을 때만 동작함");
        System.out.println("  · NoFallback (기본값) — 트랜잭션 밖에서 발행하면 리스너가 무시됨 (경고도 없음)");
        System.out.println("  · WithFallback (true) — 트랜잭션이 없어도 즉시 리스너를 실행함");
        System.out.println("  · 트랜잭션 안/밖 모두에서 호출될 가능성이 있는 이벤트라면 true 설정이 안전함");

        ctx.close();
    }
}
