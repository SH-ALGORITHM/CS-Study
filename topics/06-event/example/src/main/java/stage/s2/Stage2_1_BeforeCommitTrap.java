package stage.s2;

import infra.MeasurementLog;
import java.math.BigDecimal;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * STAGE 2-1 (Step 2 함정) — 그냥 @EventListener 만 쓰면 commit 전 발사 → 롤백돼도 이미 처리됨.
 *
 * <h3>시나리오</h3>
 * <ol>
 *   <li>placeOrder(1L, -100) — 음수 금액 (INSERT 후 일부러 예외)</li>
 *   <li>INSERT 는 트랜잭션 롤백으로 취소됨 ✓</li>
 *   <li>하지만 [알림] 은 이미 발사됨 ✗ → 사용자에게 "주문 실패" 알림이 가버림</li>
 * </ol>
 *
 * <h3>해결 → {@link Stage2_1_AfterCommit}</h3>
 */
@Configuration
@EnableAutoConfiguration
public class Stage2_1_BeforeCommitTrap {

    public record OrderPlacedEvent(Long orderId, BigDecimal amount) {}

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
        public void placeOrder(Long orderId, BigDecimal amount) {
            System.out.println("  [SERVICE] INSERT orders id=" + orderId + " amount=" + amount);
            jdbc.update("INSERT INTO orders(id, amount) VALUES (?, ?)", orderId, amount);

            System.out.println("  [SERVICE] publishEvent");
            publisher.publishEvent(new OrderPlacedEvent(orderId, amount));

            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("음수 금액 — 트랜잭션 rollback");
            }
        }

        public Integer countOrders() {
            return jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
        }
    }

    public static class NotificationListener {
        @EventListener      // ★ 그냥 @EventListener — 트랜잭션 무관
        public void on(OrderPlacedEvent e) {
            System.out.println("  [알림] 주문 발생 — id=" + e.orderId() + " / amount=" + e.amount()
                + " ← 사용자에게 이메일 발사됨 (회수 불가)");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_1_BeforeCommitTrap.class, args);
        OrderService svc = ctx.getBean(OrderService.class);

        MeasurementLog.title("STAGE 2-1 (Step 2 함정) — @EventListener 만 + rollback");

        MeasurementLog.section("정상 케이스 — amount=100");
        svc.placeOrder(1L, BigDecimal.valueOf(100));
        System.out.println("  orders 행 수: " + svc.countOrders() + " (예상: 1)");

        MeasurementLog.section("rollback 케이스 — amount=-100");
        try {
            svc.placeOrder(2L, BigDecimal.valueOf(-100));
        } catch (IllegalArgumentException ex) {
            System.out.println("  [예외] " + ex.getMessage());
        }
        System.out.println("  orders 행 수: " + svc.countOrders() + " (예상: 1, INSERT 는 rollback)");

        System.out.println();
        System.out.println("[학습 포인트] 함정 재현");
        System.out.println("  · INSERT 는 rollback ✓ / 하지만 [알림] 은 이미 발사됨 ✗");
        System.out.println("  · 5 주차 @Order 양파 (Audit 가 TX 안쪽) 와 정확히 같은 문제");
        System.out.println("  · 해결 → Stage2_1_AfterCommit");
        ctx.close();
    }
}
