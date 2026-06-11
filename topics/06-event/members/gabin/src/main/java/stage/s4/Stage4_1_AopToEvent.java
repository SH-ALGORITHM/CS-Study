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
 * STAGE 4-1 — 5 주차 @Audited (AOP, commit 전) → 6 주차 @TransactionalEventListener (commit 후) 로 옮김.
 *
 * <h3>두 서비스 비교</h3>
 * <ol>
 *   <li>OldAopService — 5 주차 패턴 @Audited (AOP). audit 가 commit 전 실행 → rollback 시 audit 남음</li>
 *   <li>NewEventService — 6 주차 패턴 publishEvent + AFTER_COMMIT. rollback 시 audit 호출 X</li>
 * </ol>
 *
 * <h3>핵심</h3>
 * 변경된 것: 메서드의 어노테이션 (@Audited 떼고 publishEvent 한 줄 추가) + 별 리스너 클래스 분리.
 */
@Configuration
@EnableAutoConfiguration
public class Stage4_1_AopToEvent {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Audited {
        String action() default "";
    }

    public record OrderCompletedEvent(Long orderId, BigDecimal amount, String action) {}

    @Bean public AuditAspect auditAspect() { return new AuditAspect(); }
    @Bean public OldAopService oldAopService(JdbcTemplate jdbc) { return new OldAopService(jdbc); }
    @Bean public NewEventService newEventService(JdbcTemplate jdbc, ApplicationEventPublisher p) {
        return new NewEventService(jdbc, p);
    }
    @Bean public AuditListener auditListener() { return new AuditListener(); }

    // ── 5 주차 패턴 ── @Audited AOP ──────────────────────────
    @Aspect
    @Order(2)
    public static class AuditAspect {
        @Around("@annotation(audited)")
        public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
            try {
                Object result = pjp.proceed();
                System.out.println("    [AOP AUDIT] action=" + audited.action()
                    + " result=SUCCESS  ← commit 전 (TX 안쪽)");
                return result;
            } catch (Throwable t) {
                System.out.println("    [AOP AUDIT] action=" + audited.action()
                    + " result=FAIL  ← commit 전 (TX 안쪽), 어찌됐든 로그 남김");
                throw t;
            }
        }
    }

    public static class OldAopService {
        private final JdbcTemplate jdbc;
        public OldAopService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @Transactional
        @Audited(action = "ORDER_COMPLETED")
        public void completeOrder(Long orderId, BigDecimal amount) {
            jdbc.update("INSERT INTO orders(id, amount) VALUES (?, ?)", orderId, amount);
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("음수 금액");
            }
        }
    }

    // ── 6 주차 패턴 ── publishEvent + @TransactionalEventListener ──
    public static class NewEventService {
        private final JdbcTemplate jdbc;
        private final ApplicationEventPublisher publisher;
        public NewEventService(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
            this.jdbc = jdbc;
            this.publisher = publisher;
        }

        @Transactional
        public void completeOrder(Long orderId, BigDecimal amount) {
            jdbc.update("INSERT INTO orders(id, amount) VALUES (?, ?)", orderId, amount);
            publisher.publishEvent(new OrderCompletedEvent(orderId, amount, "ORDER_COMPLETED"));
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("음수 금액");
            }
        }
    }

    public static class AuditListener {
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void on(OrderCompletedEvent e) {
            System.out.println("    [EVENT AUDIT] action=" + e.action()
                + " orderId=" + e.orderId() + "  ← commit 후만 호출");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage4_1_AopToEvent.class, args);
        OldAopService old = ctx.getBean(OldAopService.class);
        NewEventService neu = ctx.getBean(NewEventService.class);

        MeasurementLog.title("STAGE 4-1 — 5 주차 @Audited (AOP) → 6 주차 @TransactionalEventListener");

        MeasurementLog.section("(1) OldAopService — 정상");
        old.completeOrder(100L, BigDecimal.valueOf(100));

        MeasurementLog.section("(2) OldAopService — rollback. AOP audit 는 FAIL 로그 남김");
        try { old.completeOrder(101L, BigDecimal.valueOf(-100)); }
        catch (Exception ex) { System.out.println("    [caller] " + ex.getMessage()); }

        MeasurementLog.section("(3) NewEventService — 정상. commit 후 AUDIT");
        neu.completeOrder(200L, BigDecimal.valueOf(100));

        MeasurementLog.section("(4) NewEventService — rollback. AUDIT 호출 X");
        try { neu.completeOrder(201L, BigDecimal.valueOf(-100)); }
        catch (Exception ex) { System.out.println("    [caller] " + ex.getMessage()); }

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · 5 주차 AOP — commit 전 (TX 안쪽). 실패도 SUCCESS 도 모두 로그");
        System.out.println("  · 6 주차 Event — commit 후만. 실패 시 audit 호출 안 됨");
        System.out.println("  · 어느 쪽이 맞나? — 정책 결정: 시도 자체 기록 (AOP) vs 확정 기록 (Event)");
        MeasurementLog.record(
            "s4-1",
            "AOP audit=commit 전·rollback도 기록 / Event audit=commit 후·rollback 미호출"
        );
        ctx.close();
    }
}

