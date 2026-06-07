package stage.s4;

import domain.*;
import infra.MeasurementLog;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * STAGE 4-2 — self-invocation 해결책 실습.
 * 
 * "내 손이 내 눈을 찌를 수 없다면, 다른 사람(프록시)의 손을 빌리자!"
 */
@SpringBootApplication(scanBasePackages = "stage.s4")
@ComponentScan(
    basePackages = {"stage.s4", "domain", "infra"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
            domain.AuditAspect.class,
            domain.OrderService.class, // 기존 함정 서비스 제외
            stage.s2.Stage2_1_NaiveTrap.class,
            stage.s2.Stage2_1_ThreadLocal.class,
            stage.s2.Stage2_2_OrderChaining.class,
            stage.s2.Stage2_3_Pointcut.class,
            stage.s2.Stage2_4_FiveAdvice.class,
            stage.s2.Stage2_5_Audited.class,
            stage.s3.Stage3_1_Overhead.class,
            stage.s3.Stage3_2_JdkVsCglib.class,
            stage.s3.Stage3_3_GetClass.class,
            stage.s3.Stage3_4_BeanPostProcessors.class,
            stage.s4.Stage4_1_SelfInvocation.class
        }
    )
)
public class Stage4_2_Resolve {

    @Bean
    public MyTransactionalAspect myTransactionalAspect5(DataSource ds) {
        return new MyTransactionalAspect(ds);
    }

    // 해결책 (a): 자기 자신을 주입받기
    @Service
    public static class SelfInjectedService {
        // [핵심] 스프링이 나 대신 일해줄 '프록시'를 나에게 직접 줍니다.
        // 순환 참조 방지를 위해 @Lazy를 붙입니다.
        @Autowired @Lazy
        private SelfInjectedService self;

        @MyTransactional
        public void outer() {
            System.out.println("  [Self-Inject] outer 시작 (this = " + this.getClass().getSimpleName() + ")");
            // this.inner()가 아니라 self.inner()를 부릅니다!
            self.inner(); 
        }

        @MyTransactional
        public void inner() {
            System.out.println("  [Self-Inject] inner 호출됨 (프록시를 거쳤다면 위아래로 [TX] 로그가 보임)");
        }
    }

    // 해결책 (c): 클래스 분리 (가장 권장)
    @Service
    public static class OrderManager {
        private final InnerWorker worker;
        public OrderManager(InnerWorker worker) { this.worker = worker; }

        @MyTransactional
        public void process() {
            System.out.println("  [Split] Manager가 일감을 넘깁니다.");
            worker.doActualWork(); // 다른 빈(Bean) 호출 -> 프록시 자동 경유
        }
    }

    @Service
    public static class InnerWorker {
        @MyTransactional
        public void doActualWork() {
            System.out.println("  [Split] Worker가 실제 일을 합니다.");
        }
    }

    public static void main(String[] args) {
        OrderRepository.useNaiveMode = false;

        ConfigurableApplicationContext ctx = SpringApplication.run(Stage4_2_Resolve.class, args);

        MeasurementLog.title("STAGE 4-2 — self-invocation 해결책 테스트");

        MeasurementLog.section("방법 (a) 자기 자신 주입 (@Autowired @Lazy)");
        ctx.getBean(SelfInjectedService.class).outer();

        MeasurementLog.section("방법 (c) 클래스 분리 (가장 권장)");
        ctx.getBean(OrderManager.class).process();

        MeasurementLog.section("학습 포인트");
        System.out.println("  · self.inner() 호출 시 [TX] begin이 다시 찍히는 것을 확인!");
        System.out.println("  · 클래스 분리 방식이 가장 깔끔하고 객체 지향적인 해결책임");
        System.out.println("  · 면접 답변: '프록시 객체를 통해 외부 호출이 발생하도록 유도해야 함'");

        ctx.close();
    }
}
