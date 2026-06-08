package stage.s2;

import domain.MyTransactionalAspect;
import domain.OrderRepository;
import domain.OrderService;
import infra.MeasurementLog;
import java.math.BigDecimal;
import javax.sql.DataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    basePackages = "domain",
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = domain.AuditAspect.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = domain.CacheAspect.class)
    }
)
public class Stage2_1_ThreadLocal {

    @Bean
    public MyTransactionalAspect myTransactionalAspect(DataSource ds) {
        return new MyTransactionalAspect(ds);
    }

    public static void main(String[] args) {
        OrderRepository.useNaiveMode = false;

        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_1_ThreadLocal.class, args);
        OrderService svc = ctx.getBean(OrderService.class);

        MeasurementLog.title("STAGE 2-1 Step 3 — ThreadLocal 해결");
        MeasurementLog.row("초기 id=1 잔액", svc.getBalance(1));
        MeasurementLog.row("초기 id=2 잔액", svc.getBalance(2));

        MeasurementLog.section("transfer(1 → 2, 500) 실행, 중간에 예외 발생");
        try {
            svc.transfer(1L, 2L, new BigDecimal("500.00"), true);
        } catch (RuntimeException e) {
            System.out.println("  예외 발생: " + e.getMessage());
        }

        BigDecimal from = svc.getBalance(1);
        BigDecimal to = svc.getBalance(2);

        MeasurementLog.section("결과 확인 — ThreadLocal 의 conn 으로 묶였으므로 둘 다 그대로");
        MeasurementLog.row("id=1 잔액 (10000 으로 복원)", from);
        MeasurementLog.row("id=2 잔액 (10000 그대로)", to);

        MeasurementLog.section("학습 포인트");
        System.out.println("  · Aspect 가 TX_CONN.set(conn) 으로 ThreadLocal 에 보관");
        System.out.println("  · Repository 가 같은 conn 을 받아 minusBalance 까지 같이 rollback");
        System.out.println("  · Spring 의 TransactionSynchronizationManager 가 이 역할");

        MeasurementLog.save("s2-1", "ThreadLocal @MyTransactional 해결",
            "id1=" + from + " / id2=" + to + " / 같은 conn 공유 후 rollback");

        ctx.close();
    }
}
