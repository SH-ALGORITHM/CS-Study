package stage.s3;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STAGE 3-3 — getClass()를 통한 프록시 정체 판별 매트릭스.
 */
@SpringBootApplication(scanBasePackages = "stage.s3")
@ComponentScan(
    basePackages = {"stage.s3", "domain", "infra"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
            domain.AuditAspect.class,
            domain.MyTransactionalAspect.class,
            domain.NaiveTransactionalAspect.class,
            stage.s2.Stage2_1_NaiveTrap.class,
            stage.s2.Stage2_1_ThreadLocal.class,
            stage.s2.Stage2_2_OrderChaining.class,
            stage.s2.Stage2_3_Pointcut.class,
            stage.s2.Stage2_4_FiveAdvice.class,
            stage.s2.Stage2_5_Audited.class,
            stage.s3.Stage3_1_Overhead.class,
            stage.s3.Stage3_2_JdkVsCglib.class
        }
    )
)
public class Stage3_3_GetClass {

    // 케이스 1: 인터페이스 X + @Transactional X
    @Service
    public static class NoInterfaceNoTx {
        public String work() { return "1"; }
    }

    // 케이스 2: 인터페이스 X + @Transactional O
    @Service
    public static class NoInterfaceWithTx {
        @Transactional
        public String work() { return "2"; }
    }

    // 케이스 3: 인터페이스 O + @Transactional X
    public interface SomeService { String work(); }

    @Service
    public static class HasInterfaceNoTx implements SomeService {
        public String work() { return "3"; }
    }

    // 케이스 4: 인터페이스 O + @Transactional O
    public interface OtherService { String work(); }

    @Service
    public static class HasInterfaceWithTx implements OtherService {
        @Transactional
        public String work() { return "4"; }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_3_GetClass.class, args);

        MeasurementLog.title("STAGE 3-3 — getClass() 판별 매트릭스");
        
        MeasurementLog.row("인터페이스 X + TX X", 
            ctx.getBean(NoInterfaceNoTx.class).getClass().getSimpleName());
        
        MeasurementLog.row("인터페이스 X + TX O", 
            ctx.getBean(NoInterfaceWithTx.class).getClass().getSimpleName());
        
        MeasurementLog.row("인터페이스 O + TX X", 
            ctx.getBean(HasInterfaceNoTx.class).getClass().getSimpleName());
        
        MeasurementLog.row("인터페이스 O + TX O", 
            ctx.getBean(HasInterfaceWithTx.class).getClass().getSimpleName());

        MeasurementLog.section("학습 포인트");
        System.out.println("  · @Transactional이 없으면 부가 기능이 필요 없으므로 '진짜 객체'가 등록됨");
        System.out.println("  · @Transactional이 있으면 프록시 객체가 대신 등록됨");
        System.out.println("  · Spring Boot 2.0+는 인터페이스가 있어도 기본적으로 CGLIB($$SpringCGLIB)를 사용함");

        ctx.close();
    }
}
