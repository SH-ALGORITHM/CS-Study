package stage.s2;

import domain.MyTransactionalAspect;
import domain.OrderRepository;
import domain.OrderService;
import infra.MeasurementLog;
import java.math.BigDecimal;
import javax.sql.DataSource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * STAGE 2-2 — AOP 체이닝 + {@code @Order} advice 안-밖 순서.
 *
 * <h3>관찰 포인트</h3>
 * <pre>
 * 예상 출력 순서 (정상 종료):
 *   [TX] begin                ← @Order(1) — 가장 바깥
 *     [AUDIT] before          ← @Order(2)
 *       [TIMED] start         ← @Order(3) — 가장 안쪽
 *         실제 메서드
 *       [TIMED] end (Xms)
 *     [AUDIT] success
 *   [TX] commit
 * </pre>
 *
 * <p>트랜잭션이 가장 바깥 → 감사 / 측정이 commit 시점 못 봄.
 * commit 후 처리는 @TransactionalEventListener(AFTER_COMMIT) — 6 주차.
 */
// 다른 stage (2-1, 4-1) 와 동일 패턴 — @SpringBootApplication 의 메타 @ComponentScan 은
// 직접 선언한 @ComponentScan 으로 오버라이드됨. 두 어노테이션 합쳐서 stage.s2 + domain 둘 다 스캔.
@Configuration
@EnableAutoConfiguration
@ComponentScan(
    basePackages = {"stage.s2", "domain"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
            domain.AuditAspect.class,   // 이 클래스 안 SimpleAuditAspect 와 충돌 방지
            Stage2_1_NaiveTrap.class,
            Stage2_1_ThreadLocal.class,
            Stage2_3_Pointcut.class,
            Stage2_4_FiveAdvice.class,
            Stage2_5_Audited.class
        }
    )
)
public class Stage2_2_OrderChaining {

    @Bean
    public MyTransactionalAspect myTransactionalAspect(DataSource ds) {
        return new MyTransactionalAspect(ds);   // @Order(1) — 가장 바깥
    }

    /** @Order(2) — 트랜잭션 안쪽 / 측정 바깥쪽 */
    @Aspect
    @Component
    @Order(2)
    public static class SimpleAuditAspect {
        @Around("@annotation(domain.Audited)")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("  [AUDIT] before — " + pjp.getSignature().getName());
            try {
                Object result = pjp.proceed();
                System.out.println("  [AUDIT] success");
                return result;
            } catch (Throwable t) {
                System.out.println("  [AUDIT] fail");
                throw t;
            }
        }
    }

    /** @Order(3) — 가장 안쪽 */
    @Aspect
    @Component
    @Order(3)
    public static class TimedAspect {
        @Around("@annotation(domain.Audited)")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            long start = System.nanoTime();
            System.out.println("    [TIMED] start");
            try {
                return pjp.proceed();
            } finally {
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                System.out.println("    [TIMED] end (" + elapsedMs + "ms)");
            }
        }
    }

    public static void main(String[] args) {
        OrderRepository.useNaiveMode = false;

        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_2_OrderChaining.class, args);
        OrderService svc = ctx.getBean(OrderService.class);

        MeasurementLog.title("STAGE 2-2 — @Order advice 안-밖 순서");
        MeasurementLog.section("transfer(1 → 2, 100) 정상 종료");
        svc.transfer(1L, 2L, new BigDecimal("100.00"), false);

        MeasurementLog.section("학습 포인트");
        System.out.println("  · @Order 숫자 작은 게 가장 바깥. TX(1) > AUDIT(2) > TIMED(3)");
        System.out.println("  · TX 가 가장 바깥 → AUDIT/TIMED 가 commit 전에 실행 — 알림 / 외부 호출 주의");
        System.out.println("  · commit 후 처리 필요하면 @TransactionalEventListener (6 주차)");

        ctx.close();
    }
}
