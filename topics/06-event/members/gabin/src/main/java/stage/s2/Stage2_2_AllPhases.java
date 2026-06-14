package stage.s2;

import infra.MeasurementLog;
import java.math.BigDecimal;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * STAGE 2-2 — 4 phase 한꺼번에 출력해서 순서 확인.
 *
 * <h3>정상 commit 시 출력 순서</h3>
 * <pre>
 *   INSERT
 *   publishEvent
 *     [BEFORE_COMMIT]
 *   (commit)
 *     [AFTER_COMMIT]
 *     [AFTER_COMPLETION]
 * </pre>
 *
 * <h3>rollback 시 출력 순서</h3>
 * <pre>
 *   INSERT
 *   publishEvent
 *   (rollback)
 *     [AFTER_ROLLBACK]
 *     [AFTER_COMPLETION]
 * </pre>
 */
@Configuration
@EnableAutoConfiguration
public class Stage2_2_AllPhases {

    public record OrderCompletedEvent(Long orderId, BigDecimal amount) {}

    @Bean
    public OrderService orderService(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
        return new OrderService(jdbc, publisher);
    }

    @Bean
    public AllPhaseListener listener() { return new AllPhaseListener(); }

    public static class OrderService {
        private final JdbcTemplate jdbc;
        private final ApplicationEventPublisher publisher;
        public OrderService(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
            this.jdbc = jdbc;
            this.publisher = publisher;
        }

        @Transactional
        public void completeOrder(Long orderId, BigDecimal amount) {
            System.out.println("  [SERVICE] INSERT id=" + orderId);
            jdbc.update("INSERT INTO orders(id, amount) VALUES (?, ?)", orderId, amount);

            System.out.println("  [SERVICE] publishEvent");
            publisher.publishEvent(new OrderCompletedEvent(orderId, amount));

            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("음수 금액");
            }
            System.out.println("  [SERVICE] 메서드 종료 → commit 예정");
        }
    }

    public static class AllPhaseListener {

        @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
        public void onBefore(OrderCompletedEvent e) {
            System.out.println("    [BEFORE_COMMIT] 재고 확정 id=" + e.orderId());
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void onAfterCommit(OrderCompletedEvent e) {
            System.out.println("    [AFTER_COMMIT] 고객에게 주문완료 알림 id=" + e.orderId());
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
        public void onAfterRollback(OrderCompletedEvent e) {
            System.out.println("    [AFTER_ROLLBACK] 재고 예약 복구 id=" + e.orderId());
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
        public void onAfterCompletion(OrderCompletedEvent e) {
            System.out.println("    [AFTER_COMPLETION] 완료 시도 메트릭 기록 id=" + e.orderId());
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_2_AllPhases.class, args);
        OrderService svc = ctx.getBean(OrderService.class);

        MeasurementLog.title("STAGE 2-2 — 4 phase 호출 순서");

        MeasurementLog.section("정상 commit — amount=100");
        svc.completeOrder(10L, BigDecimal.valueOf(100));

        MeasurementLog.section("rollback — amount=-100");
        try { svc.completeOrder(11L, BigDecimal.valueOf(-100)); }
        catch (IllegalArgumentException ex) { System.out.println("  [예외] " + ex.getMessage()); }

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · 정상: BEFORE_COMMIT → (commit) → AFTER_COMMIT → AFTER_COMPLETION");
        System.out.println("  · rollback: (rollback) → AFTER_ROLLBACK → AFTER_COMPLETION");
        System.out.println("  · BEFORE_COMMIT 에서 예외 → 본 트랜잭션도 rollback");
        MeasurementLog.record(
            "s2-2",
            "정상=BEFORE_COMMIT→AFTER_COMMIT→AFTER_COMPLETION / "
                + "rollback=AFTER_ROLLBACK→AFTER_COMPLETION"
        );
        ctx.close();
    }
}

