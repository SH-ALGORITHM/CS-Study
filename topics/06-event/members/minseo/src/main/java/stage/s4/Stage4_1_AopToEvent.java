package stage.s4;

import infra.MeasurementLog;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
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
 * STAGE 4-1 — 5주차 @Audited (AOP) → 6주차 @TransactionalEventListener 전환
 * 
 * [시나리오]
 * 재고 변경 내역을 감사 로그(Audit)로 남깁니다.
 * 1. OldAopService: 5주차 방식. AOP가 트랜잭션 안쪽에서 실행됨 -> 롤백되어도 로그가 남음 (시도 기록)
 * 2. NewEventService: 6주차 방식. 커밋 후에만 리스너가 실행됨 -> 롤백되면 로그가 남지 않음 (확정 기록)
 */
@Configuration
@EnableAutoConfiguration
public class Stage4_1_AopToEvent {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Audited {
        String action() default "";
    }

    public record StockChangedEvent(Long id, int amount, String action) {}

    @Bean public AuditAspect auditAspect() { return new AuditAspect(); }
    @Bean public OldAopInventoryService oldService(JdbcTemplate jdbc) { return new OldAopInventoryService(jdbc); }
    @Bean public NewEventInventoryService newService(JdbcTemplate jdbc, ApplicationEventPublisher p) {
        return new NewEventInventoryService(jdbc, p);
    }
    @Bean public AuditListener auditListener() { return new AuditListener(); }

    // ── [5주차 패턴] @Audited AOP ──────────────────────────
    @Aspect
    @Order(2) // Transaction(1) 보다 안쪽
    public static class AuditAspect {
        @Around("@annotation(audited)")
        public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
            try {
                Object result = pjp.proceed();
                System.out.println("    [AOP AUDIT] action=" + audited.action() + " | result=SUCCESS (TX 안쪽)");
                return result;
            } catch (Throwable t) {
                System.out.println("    [AOP AUDIT] action=" + audited.action() + " | result=FAIL (TX 안쪽, 롤백 예정)");
                throw t;
            }
        }
    }

    public static class OldAopInventoryService {
        private final JdbcTemplate jdbc;
        public OldAopInventoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @Transactional
        @Audited(action = "STOCK_DECREMENT")
        public void changeStock(Long id, int amount) {
            jdbc.update("UPDATE inventory SET stock = stock - ? WHERE id = ?", amount, id);
            if (amount <= 0) throw new RuntimeException("잘못된 수량");
        }
    }

    // ── [6주차 패턴] Event + AFTER_COMMIT ──────────────────
    public static class NewEventInventoryService {
        private final JdbcTemplate jdbc;
        private final ApplicationEventPublisher publisher;
        public NewEventInventoryService(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
            this.jdbc = jdbc;
            this.publisher = publisher;
        }

        @Transactional
        public void changeStock(Long id, int amount) {
            jdbc.update("UPDATE inventory SET stock = stock - ? WHERE id = ?", amount, id);
            // 이벤트 발행 (리스너가 커밋 후에만 돌도록 설정됨)
            publisher.publishEvent(new StockChangedEvent(id, amount, "STOCK_DECREMENT"));
            
            if (amount <= 0) throw new RuntimeException("잘못된 수량");
        }
    }

    public static class AuditListener {
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void on(StockChangedEvent e) {
            System.out.println("    [EVENT AUDIT] action=" + e.action() + " | id=" + e.id() + " (커밋 확정 후 기록)");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage4_1_AopToEvent.class, args);
        OldAopInventoryService old = ctx.getBean(OldAopInventoryService.class);
        NewEventInventoryService neu = ctx.getBean(NewEventInventoryService.class);

        MeasurementLog.title("STAGE 4-1 — AOP Audit vs Event Audit");

        MeasurementLog.section("(1) Old AOP — 정상 케이스");
        old.changeStock(1L, 10);

        MeasurementLog.section("(2) Old AOP — 롤백 케이스 (AOP 로그는 남음)");
        try { old.changeStock(1L, -1); } catch (Exception ignored) {}

        MeasurementLog.section("(3) New Event — 정상 케이스");
        neu.changeStock(1L, 10);

        MeasurementLog.section("(4) New Event — 롤백 케이스 (이벤트 로그 안 남음)");
        try { neu.changeStock(1L, -1); } catch (Exception ignored) {}

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · AOP 방식: TX 안쪽에서 실행되므로 '시도(Attempt)' 자체를 기록하기 좋음");
        System.out.println("  · Event 방식: AFTER_COMMIT을 통해 '성공(Finalized)'한 기록만 남기기 좋음");
        System.out.println("  · 비즈니스 요구사항에 따라 어떤 기록 정책을 가져갈지 선택하는 것이 핵심");

        ctx.close();
    }
}
