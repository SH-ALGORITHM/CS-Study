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
 * STAGE 2-2 — AOP 체이닝 + @Order 양파 껍질.
 */
@SpringBootApplication
@ComponentScan(
    basePackages = {"stage.s2", "domain", "infra"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
            Stage2_1_NaiveTrap.class,
            Stage2_1_ThreadLocal.class
        }
    )
)
public class Stage2_2_OrderChaining {

    // 빈 이름을 'myTransactionalAspect2'로 변경하여 충돌 회피
    @Bean
    public MyTransactionalAspect myTransactionalAspect2(DataSource ds) {
        return new MyTransactionalAspect(ds);
    }

    // 빈 이름을 'auditAspect2'로 변경하여 충돌 회피
    @Bean
    public AuditAspect auditAspect2() {
        return new AuditAspect();
    }

    public static void main(String[] args) {
        OrderRepository.useNaiveMode = false;

        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_2_OrderChaining.class, args);
        OrderService svc = ctx.getBean(OrderService.class);

        MeasurementLog.title("STAGE 2-2 — @Order 양파 껍질 테스트");

        MeasurementLog.section("transfer(1 → 2, 100) 정상 실행");
        svc.transfer(1L, 2L, new BigDecimal("100.00"), false);

        ctx.close();
    }
}
