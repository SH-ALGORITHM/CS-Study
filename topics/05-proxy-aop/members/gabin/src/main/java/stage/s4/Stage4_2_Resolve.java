package stage.s4;

import domain.Audited;
import domain.MyTransactional;
import domain.MyTransactionalAspect;
import infra.MeasurementLog;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
    Stage4_2_Resolve.SelfInjectedService.class,
    Stage4_2_Resolve.OuterService.class,
    Stage4_2_Resolve.InnerService.class
})
public class Stage4_2_Resolve {

    @Bean
    public MyTransactionalAspect myTransactionalAspect(DataSource ds) {
        return new MyTransactionalAspect(ds);
    }

    @Service
    public static class SelfInjectedService {
        @Autowired
        @Lazy
        private SelfInjectedService self;

        @MyTransactional
        @Audited(action = "OUTER")
        public void outer() {
            System.out.println("  [Self-Inject] outer 시작");
            self.inner();
        }

        @MyTransactional
        @Audited(action = "INNER")
        public void inner() {
            System.out.println("  [Self-Inject] inner 호출됨");
        }
    }

    @Service
    public static class OuterService {
        private final InnerService inner;

        public OuterService(InnerService inner) {
            this.inner = inner;
        }

        @MyTransactional
        @Audited(action = "OUTER")
        public void outer() {
            System.out.println("  [Split] outer 시작");
            inner.inner();
        }
    }

    @Service
    public static class InnerService {
        @MyTransactional
        @Audited(action = "INNER")
        public void inner() {
            System.out.println("  [Split] inner 호출됨");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage4_2_Resolve.class, args);

        MeasurementLog.title("STAGE 4-2 — self-invocation 해결 3 가지");

        MeasurementLog.section("해결 (a) 자기 자신 주입 — 동작은 함 / 설계 어색");
        ctx.getBean(SelfInjectedService.class).outer();

        MeasurementLog.section("해결 (c) 클래스 분리 — 권장");
        ctx.getBean(OuterService.class).outer();

        MeasurementLog.section("학습 포인트");
        System.out.println("  · 두 해결책 모두 inner 의 [TX] begin 이 출력된다.");
        System.out.println("  · 자기 자신 주입은 동작하지만 설계가 어색하고 @Lazy가 필요하다.");
        System.out.println("  · 클래스 분리가 가장 권장되는 해결이다.");
        System.out.println("  · (b) ApplicationContext.getBean() 방식은 Service Locator라 권장하지 않는다.");

        MeasurementLog.save("s4-2", "self-invocation 해결",
            "self injection 동작 / 클래스 분리 권장");

        ctx.close();
    }
}
