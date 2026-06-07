package stage.s2;

import domain.NaiveTransactionalAspect;
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
 * STAGE 2-1 Step 1 + 2 — 순진한 버전 함정 재현.
 */
@SpringBootApplication(scanBasePackages = "stage.s2")
@ComponentScan(
    basePackages = {"stage.s2", "domain", "infra"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = domain.MyTransactionalAspect.class // 해결 버전은 제외
    )
)
public class Stage2_1_NaiveTrap {

    @Bean
    public NaiveTransactionalAspect naiveTransactionalAspect(DataSource ds) {
        return new NaiveTransactionalAspect(ds);
    }

    public static void main(String[] args) {
        OrderRepository.useNaiveMode = true; // Step 1 — Repository 가 매번 새 conn 사용

        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_1_NaiveTrap.class, args);
        OrderService svc = ctx.getBean(OrderService.class);

        MeasurementLog.title("STAGE 2-1 Step 1+2 — 순진한 버전 함정 재현");
        MeasurementLog.row("초기 id=1 잔액", svc.getBalance(1));
        MeasurementLog.row("초기 id=2 잔액", svc.getBalance(2));

        MeasurementLog.section("transfer(1 → 2, 500) 실행, 중간에 예외 발생");
        try {
            svc.transfer(1L, 2L, new BigDecimal("500.00"), true);
        } catch (RuntimeException e) {
            System.out.println("  예외 발생: " + e.getMessage());
        }

        MeasurementLog.section("결과 확인 — 트랜잭션이 묶였다면 둘 다 그대로여야 함");
        MeasurementLog.row("id=1 잔액 (10000 그대로여야 함)", svc.getBalance(1));
        MeasurementLog.row("id=2 잔액 (10000 그대로여야 함)", svc.getBalance(2));

        MeasurementLog.section("해석");
        System.out.println("  · id=1 의 잔액이 9500 으로 줄어들었다면 → 함정 재현 성공");
        System.out.println("  · Aspect 의 conn 과 Repository 의 conn 이 별개라서 rollback 효과 없음");
        System.out.println("  · 해결책 = ThreadLocal — Stage2_1_ThreadLocal 실행 필요");

        ctx.close();
    }
}
