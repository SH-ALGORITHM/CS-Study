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
 * STAGE 4-2 — AOP + Event 같이 쓰기 (실제 프로젝트 패턴)
 * 
 * [시나리오]
 * 재고 관리 시스템에서 AOP와 이벤트를 동시에 활용합니다.
 * 1. AOP (@Audited): 트랜잭션 시도 자체를 기록 (성공/실패 무관하게 진입/종료 기록)
 * 2. Event (AFTER_COMMIT): 트랜잭션 성공 시 확정 알림 및 통계 갱신
 * 3. Event (AFTER_ROLLBACK): 트랜잭션 실패 시 사후 분석을 위한 상세 로그 기록
 */
@Configuration
@EnableAutoConfiguration
public class Stage4_2_AopPlusEvent {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Audited {
        String action() default "";
    }

    public record InventoryChangeEvent(Long id, int amount, String action) {}

    @Bean public AuditAspect auditAspect() { return new AuditAspect(); }
    @Bean public InventoryService inventoryService(JdbcTemplate jdbc, ApplicationEventPublisher p) {
        return new InventoryService(jdbc, p);
    }
    @Bean public InventoryListeners listeners() { return new InventoryListeners(); }

    @Aspect
    @Order(2) // @Transactional(1) 보다 안쪽
    public static class AuditAspect {
        @Around("@annotation(audited)")
        public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
            System.out.println("    [AOP Audit] >>> 시도: " + audited.action());
            try {
                Object r = pjp.proceed();
                System.out.println("    [AOP Audit] <<< 종료: SUCCESS");
                return r;
            } catch (Throwable t) {
                System.out.println("    [AOP Audit] <<< 종료: FAIL (Rollback 예정)");
                throw t;
            }
        }
    }

    public static class InventoryService {
        private final JdbcTemplate jdbc;
        private final ApplicationEventPublisher publisher;
        public InventoryService(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
            this.jdbc = jdbc;
            this.publisher = publisher;
        }

        @Transactional
        @Audited(action = "STOCK_CHANGE")
        public void changeStock(Long id, int amount) {
            jdbc.update("UPDATE inventory SET stock = stock - ? WHERE id = ?", amount, id);
            
            // 이벤트 발행 (성공/실패 리스너가 각각 반응함)
            publisher.publishEvent(new InventoryChangeEvent(id, amount, "STOCK_CHANGE"));
            
            if (amount <= 0) {
                throw new RuntimeException("수량 오류 (음수/0 불가)");
            }
        }
    }

    public static class InventoryListeners {
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void onConfirmed(InventoryChangeEvent e) {
            System.out.println("    [EVENT:확정] ✅ 재고 변경 완료 (id=" + e.id() + ") -> 담당자 알림 발송");
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void onStatistics(InventoryChangeEvent e) {
            System.out.println("    [EVENT:통계] 📊 글로벌 재고 현황 업데이트");
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
        public void onFailureAnalysis(InventoryChangeEvent e) {
            System.out.println("    [EVENT:실패] ❌ 작업 취소됨 (id=" + e.id() + ") -> 실패 원인 분석 로그 기록");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage4_2_AopPlusEvent.class, args);
        InventoryService svc = ctx.getBean(InventoryService.class);

        MeasurementLog.title("STAGE 4-2 — AOP + Event 혼합 전략 (재고 도메인)");

        MeasurementLog.section("Case 1: 정상 처리 (amount=10)");
        svc.changeStock(1L, 10);

        System.out.println();
        MeasurementLog.section("Case 2: 롤백 처리 (amount=0)");
        try { svc.changeStock(1L, 0); }
        catch (Exception ex) { System.out.println("    [Main] Exception Caught: " + ex.getMessage()); }

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · AOP (advice 안-밖): 메서드 생명주기(진입/종료)에 관여. 트랜잭션 성공 여부와 무관한 '행위 자체' 기록");
        System.out.println("  · Event (시간축): 트랜잭션 결과(Commit/Rollback)에 따라 다른 부수 효과를 유연하게 배치");
        System.out.println("  · 두 메커니즘을 섞어 쓰면 '언제(시점)'와 '어떻게(방법)'를 완벽하게 분리한 아키텍처가 가능함");

        ctx.close();
    }
}
