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
 * STAGE 2-1 (Step 3 해결) — @TransactionalEventListener(AFTER_COMMIT) 로 commit 후만 실행.
 *
 * <h3>Stage2_1_BeforeCommitTrap 와 비교</h3>
 * <ul>
 *   <li>변경점은 어노테이션 한 줄 — @EventListener → @TransactionalEventListener(phase = AFTER_COMMIT)</li>
 *   <li>rollback 시: 리스너 호출 안 됨 → 알림 전송 X</li>
 *   <li>정상 commit 시: commit 후 호출 → 정상 알림</li>
 * </ul>
 */
@Configuration
@EnableAutoConfiguration
public class Stage2_1_AfterCommit {

    public record OrderCompletedEvent(Long orderId, BigDecimal amount) {}

    @Bean
    public OrderService orderService(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
        return new OrderService(jdbc, publisher);
    }

    @Bean
    public NotificationListener notificationListener() {
        return new NotificationListener();
    }

    public static class OrderService {
        private final JdbcTemplate jdbc;
        private final ApplicationEventPublisher publisher;
        public OrderService(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
            this.jdbc = jdbc;
            this.publisher = publisher;
        }

        @Transactional
        public void completeOrder(Long orderId, BigDecimal amount) {
            System.out.println("  [SERVICE] INSERT orders id=" + orderId + " amount=" + amount);
            jdbc.update("INSERT INTO orders(id, amount) VALUES (?, ?)", orderId, amount);

            System.out.println("  [SERVICE] publishEvent");
            publisher.publishEvent(new OrderCompletedEvent(orderId, amount));

            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("음수 금액 — 트랜잭션 rollback");
            }
        }

        public Integer countOrders() {
            return jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
        }
    }

    public static class NotificationListener {
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void on(OrderCompletedEvent e) {
            System.out.println("  [알림] 주문 완료 — id=" + e.orderId() + " / amount=" + e.amount()
                + " ← commit 후만 호출됨");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_1_AfterCommit.class, args);
        OrderService svc = ctx.getBean(OrderService.class);

        MeasurementLog.title("STAGE 2-1 (Step 3 해결) — @TransactionalEventListener(AFTER_COMMIT)");

        MeasurementLog.section("정상 케이스 — amount=100");
        svc.completeOrder(1L, BigDecimal.valueOf(100));
        System.out.println("  orders 행 수: " + svc.countOrders() + " (예상: 1)");

        MeasurementLog.section("rollback 케이스 — amount=-100");
        try {
            svc.completeOrder(2L, BigDecimal.valueOf(-100));
        } catch (IllegalArgumentException ex) {
            System.out.println("  [예외] " + ex.getMessage());
        }
        int orderCount = svc.countOrders();
        System.out.println("  orders 행 수: " + orderCount + " (예상: 1, INSERT 는 rollback)");
        MeasurementLog.record(
            "s2-1",
            "@TransactionalEventListener(AFTER_COMMIT) — rollback 후 orders="
                + orderCount + " / rollback 이벤트 호출 X"
        );

        System.out.println();
        System.out.println("[학습 포인트] 5 주차 advice 안-밖 순서의 한계 해소");
        System.out.println("  · rollback 시 [알림] 호출 X — 외부 부수 효과 안전");
        System.out.println("  · 변경점은 어노테이션 한 줄 (@EventListener → @TransactionalEventListener)");
        System.out.println("  · 내부 메커니즘: TransactionSynchronization 콜백 등록 (5 주차 ThreadLocal 의 실무 추상화)");
        ctx.close();
    }
}

