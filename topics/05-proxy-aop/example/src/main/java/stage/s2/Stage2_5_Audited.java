package stage.s2;

import domain.MyTransactionalAspect;
import domain.OrderRepository;
import domain.OrderService;
import infra.MeasurementLog;
import java.math.BigDecimal;
import javax.sql.DataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * STAGE 2-5 — 본인 도메인 어노테이션 자작 적용.
 *
 * <p>{@code @Audited(action = "TRANSFER")} 가 OrderService.transfer() 에 붙어있음.
 * AuditAspect 가 호출 전후로 사용자 ID / 메서드 / 인자 / 결과 / 실행 시간 자동 기록.
 *
 * <p>여기서는 AuditAspect 도 활성화 → MyTransactionalAspect(@Order 1) + AuditAspect(@Order 2)
 * 양파 껍질 동작 + 감사 로그 형식 확인.
 */
@Configuration
@EnableAutoConfiguration
@ComponentScan(
    basePackages = {"stage.s2", "domain"},
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
    public MyTransactionalAspect myTransactionalAspect(DataSource ds) {
        return new MyTransactionalAspect(ds);
    }

    public static void main(String[] args) {
        OrderRepository.useNaiveMode = false;

        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_5_Audited.class, args);
        OrderService svc = ctx.getBean(OrderService.class);

        MeasurementLog.title("STAGE 2-5 — @Audited 자작 어노테이션 적용");

        MeasurementLog.section("정상 종료 케이스 — transfer(1 → 2, 200)");
        svc.transfer(1L, 2L, new BigDecimal("200.00"), false);

        MeasurementLog.section("예외 발생 케이스 — transfer(1 → 2, 300, fail)");
        try {
            svc.transfer(1L, 2L, new BigDecimal("300.00"), true);
        } catch (RuntimeException e) {
            System.out.println("  catch: " + e.getMessage());
        }

        MeasurementLog.section("결과 확인");
        MeasurementLog.row("id=1 잔액 (10000 - 200 = 9800)", svc.getBalance(1));
        MeasurementLog.row("id=2 잔액 (10000 + 200 = 10200)", svc.getBalance(2));

        MeasurementLog.section("학습 포인트");
        System.out.println("  · @Audited 가 메서드에 붙어있기만 하면 모든 호출이 자동 기록");
        System.out.println("  · AuditAspect 는 다른 모든 Service 에 끼울 수 있음 (재사용성)");
        System.out.println("  · 비즈니스 로직 (OrderService.transfer) 에 감사 / 트랜잭션 / 권한 코드 침입 X");

        ctx.close();
    }
}
