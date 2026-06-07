package stage.s2;

import domain.*;
import infra.MeasurementLog;
import java.math.BigDecimal;
import javax.sql.DataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * STAGE 2-5 — 본인 도메인(@Audited) 적용 최종 확인.
 */
@SpringBootApplication(scanBasePackages = "stage.s2")
@ComponentScan(
    basePackages = {"stage.s2", "domain", "infra"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
            Stage2_1_NaiveTrap.class,
            Stage2_1_ThreadLocal.class,
            Stage2_2_OrderChaining.class,
            Stage2_3_Pointcut.class,
            Stage2_4_FiveAdvice.class
        }
    )
)
public class Stage2_5_Audited {

    @Bean
    public MyTransactionalAspect myTransactionalAspect3(DataSource ds) {
        return new MyTransactionalAspect(ds); // @Order(1)
    }

    @Bean
    public AuditAspect auditAspect3() {
        return new AuditAspect(); // @Order(2)
    }

    public static void main(String[] args) {
        OrderRepository.useNaiveMode = false; // ThreadLocal 해결 모드

        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_5_Audited.class, args);
        OrderService svc = ctx.getBean(OrderService.class);

        MeasurementLog.title("STAGE 2-5 — @Audited 감사 로그 최종 확인");

        MeasurementLog.section("1. 정상 송금 케이스 — transfer(1 → 2, 200)");
        svc.transfer(1L, 2L, new BigDecimal("200.00"), false);

        MeasurementLog.section("2. 예외 발생 케이스 — transfer(1 → 2, 300, fail)");
        try {
            svc.transfer(1L, 2L, new BigDecimal("300.00"), true);
        } catch (RuntimeException e) {
            System.out.println("  Main에서 예외 감지: " + e.getMessage());
        }

        MeasurementLog.section("최종 잔액 확인");
        MeasurementLog.row("id=1 잔액 (10000 - 200 = 9800)", svc.getBalance(1));
        MeasurementLog.row("id=2 잔액 (10000 + 200 = 10200)", svc.getBalance(2));

        MeasurementLog.section("학습 포인트");
        System.out.println("  · 비즈니스 로직(OrderService)은 오직 '돈 이동'에만 집중");
        System.out.println("  · 감사 로그와 트랜잭션 처리는 AOP가 밖에서 투명하게 처리");
        System.out.println("  · 예외 발생 시 트랜잭션은 롤백되지만, 감사 로그는 FAIL 상태로 기록됨");

        ctx.close();
    }
}
