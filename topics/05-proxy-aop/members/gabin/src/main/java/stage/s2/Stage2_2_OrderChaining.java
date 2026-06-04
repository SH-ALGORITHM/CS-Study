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
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    basePackages = "domain",
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = domain.AuditAspect.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = domain.CacheAspect.class)
    }
)
@Import({Stage2_2_OrderChaining.SimpleAuditAspect.class, Stage2_2_OrderChaining.TimedAspect.class})
public class Stage2_2_OrderChaining {

    @Bean
    public MyTransactionalAspect myTransactionalAspect(DataSource ds) {
        return new MyTransactionalAspect(ds);
    }

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

        MeasurementLog.title("STAGE 2-2 — @Order 양파 껍질");
        MeasurementLog.section("transfer(1 → 2, 100) 정상 종료");
        svc.transfer(1L, 2L, new BigDecimal("100.00"), false);

        MeasurementLog.section("학습 포인트");
        System.out.println("  · @Order 숫자 작은 게 가장 바깥. TX(1) > AUDIT(2) > TIMED(3)");
        System.out.println("  · TX 가 가장 바깥이면 commit 이 가장 나중에 실행된다.");

        MeasurementLog.save("s2-2", "@Order 양파 껍질",
            "TX(1) > AUDIT(2) > TIMED(3) 순서 확인");

        ctx.close();
    }
}
