package stage.s4;

import domain.*;
import infra.MeasurementLog;
import javax.sql.DataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * STAGE 4-1 — self-invocation 함정 재현.
 * 
 * 5주차 학습의 꽃입니다. @Transactional이 왜 같은 클래스 내부 호출에선 안 먹히는지 직접 봅니다.
 */
@SpringBootApplication(scanBasePackages = "stage.s4")
@ComponentScan(
    basePackages = {"stage.s4", "domain", "infra"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
            domain.AuditAspect.class, // 출력 단순화를 위해 감사 로그는 잠시 뺍니다
            stage.s2.Stage2_1_NaiveTrap.class,
            stage.s2.Stage2_1_ThreadLocal.class,
            stage.s2.Stage2_2_OrderChaining.class,
            stage.s2.Stage2_3_Pointcut.class,
            stage.s2.Stage2_4_FiveAdvice.class,
            stage.s2.Stage2_5_Audited.class,
            stage.s3.Stage3_1_Overhead.class,
            stage.s3.Stage3_2_JdkVsCglib.class,
            stage.s3.Stage3_3_GetClass.class,
            stage.s3.Stage3_4_BeanPostProcessors.class
        }
    )
)
public class Stage4_1_SelfInvocation {

    @Bean
    public MyTransactionalAspect myTransactionalAspect4(DataSource ds) {
        return new MyTransactionalAspect(ds);
    }

    public static void main(String[] args) {
        OrderRepository.useNaiveMode = false;

        ConfigurableApplicationContext ctx = SpringApplication.run(Stage4_1_SelfInvocation.class, args);
        OrderService svc = ctx.getBean(OrderService.class);

        MeasurementLog.title("STAGE 4-1 — self-invocation 함정 재현");
        
        // 바깥에서 본 객체의 정체
        MeasurementLog.row("svc.getClass() (바깥에서 본 나)", svc.getClass().getSimpleName());

        MeasurementLog.section("svc.outerMethod(1L) 호출 시작");
        svc.outerMethod(1L);

        MeasurementLog.section("학습 포인트");
        System.out.println("  · svc.outerMethod()는 [TX] begin이 찍힘 (외부에서 프록시를 통해 들어왔기 때문)");
        System.out.println("  · 하지만 outerMethod 안에서 부른 innerMethod는 [TX] begin이 안 찍힘!");
        System.out.println("  · 원인: 클래스 내부의 this는 프록시가 아닌 '진짜 객체'이기 때문");

        ctx.close();
    }
}
