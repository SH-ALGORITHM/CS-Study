package stage.s1;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.annotation.Transactional;

/**
 * STAGE 1-3 — Spring AOP 가 자동으로 생성하는 프록시 확인.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>@Transactional 이 붙은 Service 의 getBean(...).getClass() = X$$SpringCGLIB$$0 (Spring 6+)</li>
 *   <li>(Spring 5 까지는 X$$EnhancerBySpringCGLIB$$randomHash. 6 부터 단순화)</li>
 *   <li>컨테이너가 반환하는 객체가 진짜 클래스 X — 외부에서 진짜를 볼 수 없음</li>
 *   <li>@Transactional 모두 제거하면 진짜 객체 반환 (프록시 X)</li>
 * </ul>
 *
 * <p>component scan 범위를 좁히려고 stage.s1 패키지의 Bean 만 등록.
 * domain/ 의 AuditAspect 등 다른 Aspect 가 끼어들지 않도록.
 */
@SpringBootApplication(scanBasePackages = "stage.s1")
public class Stage1SpringProxy {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage1SpringProxy.class, args);

        TxService txService = ctx.getBean(TxService.class);
        PlainService plainService = ctx.getBean(PlainService.class);

        MeasurementLog.title("Spring AOP 가 만든 프록시 확인");
        MeasurementLog.row("TxService (@Transactional 있음)", MeasurementLog.classOf(txService));
        MeasurementLog.row("PlainService (@Transactional 없음)", MeasurementLog.classOf(plainService));

        MeasurementLog.section("학습 포인트");
        System.out.println("  · @Transactional 있는 Bean → 프록시 (Spring 6: X$$SpringCGLIB$$0)");
        System.out.println("  · @Transactional 없는 Bean → 진짜 클래스 (프록시 X — 가로챌 advice 없으면 안 만듦)");
        System.out.println("  · Spring Boot 2.0+ 기본 = CGLIB. application.properties 의 spring.aop.proxy-target-class=true");
        System.out.println("  · 정확한 접미사는 버전마다 다름. 직접 출력으로 확인이 본 스터디 콘셉트");

        ctx.close();
    }

    /** @Transactional 있음 → Spring 이 프록시로 교체 */
    @org.springframework.stereotype.Service
    public static class TxService {
        @Transactional
        public String doWork() { return "tx"; }
    }

    /** @Transactional 없음 → 가로챌 advice 없으면 프록시 X */
    @org.springframework.stereotype.Service
    public static class PlainService {
        public String doWork() { return "plain"; }
    }
}
