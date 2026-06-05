package stage.s4;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STAGE 4-3 — CGLIB 한계 — final / private / static.
 *
 * <h3>관찰</h3>
 * <ul>
 *   <li>final 클래스 — 부팅 실패 (Cannot subclass final class) — 코드 주석으로만</li>
 *   <li>final 메서드 — 부팅 성공. WARN 로그 후 advice 스킵</li>
 *   <li>private 메서드 — 외부 호출 불가. CGLIB 가 자식 클래스에서 오버라이드 불가</li>
 *   <li>static 메서드 — 객체 메서드 아님. 프록시 적용 자체 불가</li>
 * </ul>
 *
 * <p>final 클래스 케이스는 코드로 활성화하면 컨테이너 부팅 자체가 실패하므로 주석으로만 명시.
 */
@Configuration
@EnableAutoConfiguration
@org.springframework.context.annotation.ComponentScan(
    basePackages = "stage.s4",
    excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = {Stage4_1_SelfInvocation.class, Stage4_2_Resolve.class}
    )
)
public class Stage4_3_CglibLimits {

    // ===== final 클래스 케이스 =====
    // @Service public static final class CannotProxyFinalClass {
    //     @Transactional public void method() {}
    // }
    // → 부팅 시 "Cannot subclass final class" 예외

    @Service
    public static class MixedMethodService {
        // (1) 일반 public — advice 정상 적용
        @Transactional
        public String publicMethod() {
            System.out.println("  publicMethod 실행 — TX 가로채야 정상");
            return "public";
        }

        // (2) final public — WARN 로그 + advice 스킵
        @Transactional
        public final String finalMethod() {
            System.out.println("  finalMethod 실행 — TX 가로채지 못함");
            return "final";
        }

        // (3) private — 외부에서 호출 자체 못함. callPrivate() 통해 우회 호출
        @Transactional
        private String privateMethod() {
            System.out.println("  privateMethod 실행 — TX 가로채지 못함");
            return "private";
        }
        public String callPrivate() { return privateMethod(); }

        // (4) static — 객체 메서드 아님
        @Transactional
        public static String staticMethod() {
            System.out.println("  staticMethod 실행 — TX 가로채지 못함");
            return "static";
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage4_3_CglibLimits.class, args);
        MixedMethodService svc = ctx.getBean(MixedMethodService.class);

        MeasurementLog.title("STAGE 4-3 — CGLIB 한계 (final / private / static)");
        MeasurementLog.row("svc.getClass() (프록시)", svc.getClass().getName());

        MeasurementLog.section("(1) public — TX 정상 적용");
        svc.publicMethod();

        MeasurementLog.section("(2) final — TX 적용 안 됨 (Spring WARN 로그 부팅 시 출력)");
        svc.finalMethod();

        MeasurementLog.section("(3) private (callPrivate 우회) — TX 적용 안 됨");
        svc.callPrivate();

        MeasurementLog.section("(4) static — TX 적용 안 됨");
        MixedMethodService.staticMethod();

        MeasurementLog.section("학습 포인트");
        System.out.println("  · final 클래스 — 부팅 실패 (Cannot subclass final class) — 코드 주석으로만");
        System.out.println("  · final 메서드 — 부팅 성공 + 부팅 시 WARN 로그 + advice 스킵");
        System.out.println("  · private — CGLIB 가 자식 클래스에서 오버라이드 자체 불가");
        System.out.println("  · static — 객체 메서드 아님 — 프록시 의미 X");
        System.out.println("  · 면접 단골: @Transactional 안 먹는 3 가지 (self-invocation / final / private)");

        ctx.close();
    }
}
