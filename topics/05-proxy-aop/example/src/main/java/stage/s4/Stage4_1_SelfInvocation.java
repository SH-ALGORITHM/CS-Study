package stage.s4;

import domain.MyTransactionalAspect;
import domain.OrderRepository;
import domain.OrderService;
import infra.MeasurementLog;
import javax.sql.DataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * STAGE 4-1 — self-invocation 함정 재현.
 *
 * <h3>관찰 포인트 (★ 학습 본질)</h3>
 * <pre>
 * svc.outerMethod() 호출:
 *   [TX] begin — outerMethod   ← svc (프록시) 가 외부 호출 가로챔
 *   [OrderService] outerMethod 시작
 *     this.getClass() = domain.OrderService   ← 원본! (프록시 아님)
 *   [OrderService] innerMethod 호출됨 — but [TX] begin 출력 X
 *                                       ← this.innerMethod() 가 프록시 우회
 *   [TX] commit
 * </pre>
 *
 * <h3>핵심 메커니즘</h3>
 * <ul>
 *   <li>{@code svc} (= {@code ctx.getBean(OrderService.class)}) = 프록시 객체</li>
 *   <li>프록시가 advice 실행 후 <strong>원본 인스턴스에 위임</strong> ({@code target.outerMethod()})</li>
 *   <li>원본 메서드 안의 {@code this} = <strong>원본 OrderService</strong> (프록시 X)</li>
 *   <li>→ {@code this.innerMethod()} 는 원본의 메서드 직접 호출 → 프록시 안 거침 → advice 무시</li>
 * </ul>
 *
 * <p>대비: 바깥에서 본 {@code svc.getClass()} = 프록시 / 안에서 본 {@code this.getClass()} = 원본.
 * 이 대비가 self-invocation 함정의 전부.
 */
@Configuration
@EnableAutoConfiguration
@ComponentScan(
    basePackages = {"stage.s4", "domain"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
            domain.AuditAspect.class,
            Stage4_2_Resolve.class,
            Stage4_3_CglibLimits.class
        }
    )
)
public class Stage4_1_SelfInvocation {

    @Bean
    public MyTransactionalAspect myTransactionalAspect(DataSource ds) {
        return new MyTransactionalAspect(ds);
    }

    public static void main(String[] args) {
        OrderRepository.useNaiveMode = false;

        ConfigurableApplicationContext ctx = SpringApplication.run(Stage4_1_SelfInvocation.class, args);
        OrderService svc = ctx.getBean(OrderService.class);

        MeasurementLog.title("STAGE 4-1 — self-invocation 함정 재현");
        MeasurementLog.row("svc.getClass() (프록시)", svc.getClass().getName());

        MeasurementLog.section("svc.outerMethod(1L) 호출");
        svc.outerMethod(1L);

        MeasurementLog.section("학습 포인트");
        System.out.println("  · outerMethod 의 [TX] begin 만 출력됨 — innerMethod 의 [TX] 없음");
        System.out.println("  · this.innerMethod() 는 프록시 우회 (this 가 프록시여도)");
        System.out.println("  · 면접 단골: @Transactional 이 안 먹는 3 가지 중 하나");
        System.out.println("  · 해결책 = Stage4_2_Resolve 참고");

        ctx.close();
    }
}
