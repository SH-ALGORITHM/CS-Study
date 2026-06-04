package stage.s4;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(Stage4_3_CglibLimits.MixedMethodService.class)
public class Stage4_3_CglibLimits {

    // final 클래스 케이스:
    // @Service public static final class CannotProxyFinalClass {
    //     @Transactional public void method() {}
    // }
    // 활성화하면 CGLIB 가 상속할 수 없어 부팅 실패.

    @Service
    public static class MixedMethodService {
        @Transactional
        public String publicMethod() {
            System.out.println("  publicMethod 실행 — TX 가로채야 정상");
            return "public";
        }

        @Transactional
        public final String finalMethod() {
            System.out.println("  finalMethod 실행 — TX 가로채지 못함");
            return "final";
        }

        @Transactional
        private String privateMethod() {
            System.out.println("  privateMethod 실행 — TX 가로채지 못함");
            return "private";
        }

        public String callPrivate() {
            return privateMethod();
        }

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

        MeasurementLog.section("(2) final — TX 적용 안 됨");
        svc.finalMethod();

        MeasurementLog.section("(3) private (callPrivate 우회) — TX 적용 안 됨");
        svc.callPrivate();

        MeasurementLog.section("(4) static — TX 적용 안 됨");
        MixedMethodService.staticMethod();

        MeasurementLog.section("학습 포인트");
        System.out.println("  · final 클래스는 CGLIB가 상속할 수 없어 부팅 실패한다.");
        System.out.println("  · final 메서드는 오버라이드할 수 없어 advice가 스킵된다.");
        System.out.println("  · private 메서드는 자식 클래스에서 오버라이드할 수 없다.");
        System.out.println("  · static 메서드는 객체 메서드가 아니라 프록시 적용 대상이 아니다.");

        MeasurementLog.save("s4-3", "CGLIB 한계",
            "final / private / static 은 프록시 적용 한계 확인");

        ctx.close();
    }
}
