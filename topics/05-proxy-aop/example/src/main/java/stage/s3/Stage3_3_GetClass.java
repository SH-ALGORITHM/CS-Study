package stage.s3;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STAGE 3-3 — ctx.getBean(X.class).getClass() 출력 매트릭스.
 *
 * <h3>케이스</h3>
 * <ul>
 *   <li>인터페이스 X + @Transactional X → 진짜 클래스</li>
 *   <li>인터페이스 X + @Transactional O → X$$SpringCGLIB$$0 (Spring 6+)</li>
 *   <li>인터페이스 O + @Transactional X → 진짜 클래스</li>
 *   <li>인터페이스 O + @Transactional O → X$$SpringCGLIB$$0 (Spring Boot 2.0+ 기본 CGLIB)</li>
 * </ul>
 *
 * <p>Spring 5 까지 접미사는 EnhancerBySpringCGLIB$$randomHash. 6 부터 단순화.
 * 정확한 접미사는 버전마다 다르므로 직접 출력 확인.
 */
@Configuration
@EnableAutoConfiguration
@org.springframework.context.annotation.ComponentScan(
    basePackages = "stage.s3",
    excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = {Stage3_1_Overhead.class, Stage3_4_BeanPostProcessors.class}
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

        MeasurementLog.title("STAGE 3-3 — getClass() 매트릭스 (Spring Boot 2.0+ / CGLIB 기본)");
        MeasurementLog.row("인터페이스 X + TX X",
            ctx.getBean(NoInterfaceNoTx.class).getClass().getName());
        MeasurementLog.row("인터페이스 X + TX O",
            ctx.getBean(NoInterfaceWithTx.class).getClass().getName());
        MeasurementLog.row("인터페이스 O + TX X",
            ctx.getBean(HasInterfaceNoTx.class).getClass().getName());
        MeasurementLog.row("인터페이스 O + TX O",
            ctx.getBean(HasInterfaceWithTx.class).getClass().getName());

        MeasurementLog.section("학습 포인트");
        System.out.println("  · @Transactional 없으면 — 가로챌 advice 없으므로 프록시 X");
        System.out.println("  · @Transactional 있으면 — Spring Boot 2.0+ 는 인터페이스 유무 무관 CGLIB");
        System.out.println("  · spring.aop.proxy-target-class=false 로 강제하면 인터페이스 있는 케이스가 JDK Proxy");

        ctx.close();
    }
}
