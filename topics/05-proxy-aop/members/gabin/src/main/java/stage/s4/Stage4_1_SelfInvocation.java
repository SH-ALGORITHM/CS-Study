package stage.s4;

import domain.MyTransactionalAspect;
import domain.OrderRepository;
import domain.OrderService;
import infra.MeasurementLog;
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
        System.out.println("  · outerMethod 의 [TX] begin 만 출력된다.");
        System.out.println("  · innerMethod 는 this.innerMethod() 로 호출되어 프록시를 우회한다.");
        System.out.println("  · this.getClass() 는 원본 OrderService 로 출력된다.");

        MeasurementLog.save("s4-1", "self-invocation 함정",
            "outerMethod TX 적용 / innerMethod self 호출은 프록시 우회");

        ctx.close();
    }
}
