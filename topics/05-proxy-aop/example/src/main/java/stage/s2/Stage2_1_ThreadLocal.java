package stage.s2;

import domain.MyTransactionalAspect;
import domain.OrderRepository;
import domain.OrderService;
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
 * STAGE 2-1 Step 3 — ThreadLocal 로 Connection 공유.
 *
 * <h3>차이점 (vs Naive)</h3>
 * <ul>
 *   <li>OrderRepository.useNaiveMode = false → Repository 가 ThreadLocal 의 conn 사용</li>
 *   <li>같은 시나리오인데 id=1 의 잔액이 10000 으로 복원됨 (정상 rollback)</li>
 * </ul>
 *
 * <p>이것이 Spring 의 {@code TransactionSynchronizationManager} 본질.
 */
@SpringBootApplication(scanBasePackages = "stage.s2")
@ComponentScan(
    basePackages = {"stage.s2", "domain"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = domain.AuditAspect.class
    )
)
public class Stage2_1_ThreadLocal {

    @Bean
    public MyTransactionalAspect myTransactionalAspect(DataSource ds) {
        return new MyTransactionalAspect(ds);
    }

    public static void main(String[] args) {
        OrderRepository.useNaiveMode = false;   // Step 3 — ThreadLocal 의 conn 공유

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

        MeasurementLog.section("결과 확인 — ThreadLocal 의 conn 으로 묶였으므로 둘 다 그대로");
        MeasurementLog.row("id=1 잔액 (10000 으로 복원)", svc.getBalance(1));
        MeasurementLog.row("id=2 잔액 (10000 그대로)", svc.getBalance(2));

        MeasurementLog.section("학습 포인트");
        System.out.println("  · Aspect 가 TX_CONN.set(conn) 으로 ThreadLocal 에 보관");
        System.out.println("  · Repository 가 MyTransactionalAspect.currentConnection(ds) 로 같은 conn 받음");
        System.out.println("  · Aspect 의 conn.rollback() 이 minusBalance 까지 같이 롤백");
        System.out.println("  · 이것이 Spring TransactionSynchronizationManager 의 본질");
        System.out.println("  · DataSourceUtils.getConnection(ds) 가 실무 추상화");

        ctx.close();
    }
}
