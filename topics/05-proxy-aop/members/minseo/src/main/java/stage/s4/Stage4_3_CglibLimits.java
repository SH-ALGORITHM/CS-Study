package stage.s4;

import domain.MyTransactional;
import domain.MyTransactionalAspect;
import domain.OrderRepository;
import infra.MeasurementLog;
import javax.sql.DataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Service;

/**
 * STAGE 4-3 — CGLIB의 한계 실습 (final / private / static).
 * 
 * CGLIB는 '상속'을 이용하기 때문에 자바의 문법적 제약을 그대로 받습니다.
 */
@SpringBootApplication(scanBasePackages = "stage.s4")
@ComponentScan(
    basePackages = {"stage.s4", "domain", "infra"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
            domain.AuditAspect.class,
            domain.OrderService.class,
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
            stage.s4.Stage4_1_SelfInvocation.class,
            stage.s4.Stage4_2_Resolve.class
        }
    )
)
public class Stage4_3_CglibLimits {

    @Bean
    public MyTransactionalAspect myTransactionalAspect6(DataSource ds) {
        return new MyTransactionalAspect(ds);
    }

    // [참고] 클래스 자체에 final을 붙이면 CGLIB가 상속을 못 해서 부팅 시 에러가 납니다.
    // public final class FinalClassService { ... }

    @Service
    public static class LimitTestService {

        // 1. 일반 public 메서드 -> 정상 작동
        @MyTransactional
        public void normalMethod() {
            System.out.println("  [Method] normalMethod 실행");
        }

        // 2. final 메서드 -> 오버라이딩 불가로 프록시가 가로채지 못함
        @MyTransactional
        public final void finalMethod() {
            System.out.println("  [Method] finalMethod 실행 (AOP가 무시될 예정)");
        }

        // 3. private 메서드 -> 자식(프록시)에서 접근 불가로 가로채지 못함
        @MyTransactional
        private void privateMethod() {
            System.out.println("  [Method] privateMethod 실행 (AOP가 무시될 예정)");
        }

        // 외부 호출용
        public void callPrivate() {
            this.privateMethod();
        }

        // 4. static 메서드 -> 객체 단위가 아니므로 프록시 적용 불가
        @MyTransactional
        public static void staticMethod() {
            System.out.println("  [Method] staticMethod 실행 (AOP가 무시될 예정)");
        }
    }

    public static void main(String[] args) {
        OrderRepository.useNaiveMode = false;

        ConfigurableApplicationContext ctx = SpringApplication.run(Stage4_3_CglibLimits.class, args);
        LimitTestService svc = ctx.getBean(LimitTestService.class);

        MeasurementLog.title("STAGE 4-3 — CGLIB 기술적 한계 테스트");

        MeasurementLog.section("1. normalMethod() - [TX] 로그가 보여야 함");
        svc.normalMethod();

        MeasurementLog.section("2. finalMethod() - [TX] 로그가 안 보여야 함");
        svc.finalMethod();

        MeasurementLog.section("3. privateMethod() - [TX] 로그가 안 보여야 함");
        svc.callPrivate();

        MeasurementLog.section("4. staticMethod() - [TX] 로그가 안 보여야 함");
        LimitTestService.staticMethod();

        MeasurementLog.section("학습 포인트");
        System.out.println("  · CGLIB는 상속(extends)을 사용하므로 final/private 메서드는 가로챌 수 없음");
        System.out.println("  · static 메서드는 인스턴스 메서드가 아니므로 프록시 메커니즘 자체가 동작 안 함");
        System.out.println("  · @Transactional이 안 먹는 3대장: self-invocation, private, final");

        ctx.close();
    }
}
