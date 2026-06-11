package stage.s4;

import infra.MeasurementLog;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * STAGE 4-2 — AOP + Event 같이 쓰기 (실제 프로젝트 패턴).
 *
 * <h3>한 메서드의 처리 흐름</h3>
 * <pre>
 *   @Audited (AOP, @Order 2)  ← 안쪽 (TX 안. commit 보장 필요 없음, 시도 자체 기록)
 *   @Transactional             ← advice 안-밖 중 가장 바깥
 *   completeOrder(...) {
 *     UPDATE orders;
 *     publishEvent(OrderCompletedEvent)  ← commit 후 알림 / 통계 / 실패 보상
 *   }
 *                                  ↓
 *   [AFTER_COMMIT] 주문완료 알림 / 매출 통계
 *   [AFTER_ROLLBACK] 재고 예약 복구
 * </pre>
 *
 * <h3>advice 안-밖 + 시간축 (commit 전 / 후) 가 직교 (orthogonal)</h3>
 */
@Configuration
@EnableAutoConfiguration
public class Stage4_2_AopPlusEvent {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Audited {
        String action() default "";
    }

    public record OrderCompletedEvent(Long orderId, BigDecimal amount) {}

    @Bean public AuditAspect auditAspect() { return new AuditAspect(); }
    @Bean public OrderService orderService(JdbcTemplate jdbc, ApplicationEventPublisher p) {
        return new OrderService(jdbc, p);
    }
    @Bean public OrderCompletionListeners listeners() { return new OrderCompletionListeners(); }

    @Aspect
    @Order(2)
    public static class AuditAspect {
        @Around("@annotation(audited)")
        public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
            System.out.println("    [AOP AUDIT begin] action=" + audited.action());
            try {
                Object r = pjp.proceed();
                System.out.println("    [AOP AUDIT end] action=" + audited.action() + " result=SUCCESS");
                return r;
            } catch (Throwable t) {
                System.out.println("    [AOP AUDIT end] action=" + audited.action() + " result=FAIL");
                throw t;
            }
        }
    }

    public static class OrderService {
        private final JdbcTemplate jdbc;
        private final ApplicationEventPublisher publisher;
        public OrderService(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
            this.jdbc = jdbc;
            this.publisher = publisher;
        }

        @Transactional
        @Audited(action = "COMPLETE_ORDER")
        public void completeOrder(Long orderId, BigDecimal amount) {
            jdbc.update("INSERT INTO orders(id, amount, status) VALUES (?, ?, 'COMPLETED')",
                orderId, amount);
            publisher.publishEvent(new OrderCompletedEvent(orderId, amount));
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("음수 금액");
            }
        }
    }

    public static class OrderCompletionListeners {
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void onNotification(OrderCompletedEvent e) {
            System.out.println("    [주문완료 알림] id=" + e.orderId() + " — 고객에게 전송");
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void onSalesStatistics(OrderCompletedEvent e) {
            System.out.println("    [매출 통계] id=" + e.orderId() + " amount=" + e.amount());
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
        public void onCompensation(OrderCompletedEvent e) {
            System.out.println("    [보상] id=" + e.orderId() + " — 재고 예약 복구");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage4_2_AopPlusEvent.class, args);
        OrderService svc = ctx.getBean(OrderService.class);

        MeasurementLog.title("STAGE 4-2 — AOP + Event 동시 사용");

        MeasurementLog.section("정상 — amount=100");
        svc.completeOrder(1L, BigDecimal.valueOf(100));

        MeasurementLog.section("rollback — amount=-100");
        try { svc.completeOrder(2L, BigDecimal.valueOf(-100)); }
        catch (Exception ex) { System.out.println("    [caller] " + ex.getMessage()); }

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · AOP (advice 안-밖) — 메서드 진입 / 종료 (commit 전). 시도 자체 기록");
        System.out.println("  · Event (시간축) — commit 후 주문완료 알림 / rollback 시 재고 보상");
        System.out.println("  · 두 메커니즘이 직교 — 함께 쓰면 깔끔하게 책임 분리");
        MeasurementLog.record(
            "s4-2",
            "AOP=메서드 진입·종료 / Event=commit 알림·rollback 보상 동시 적용"
        );
        ctx.close();
    }
}

