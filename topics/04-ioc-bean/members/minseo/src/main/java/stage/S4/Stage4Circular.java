package stage.S4;

import infra.MeasurementLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

/**
 * STAGE 4: 순환 참조 (Circular Reference) 재현
 *
 * A가 B를 부르고, B가 A를 부르는 무한 루프 상황을 만듭니다.
 * 이 파일에서는 1. 생성자 주입의 경우와 2. 필드 주입의 경우 스프링이 어떻게 반응하는지 실험합니다.
 */
public class Stage4Circular {

    // ==========================================
    // 시나리오 1: 생성자 주입에서의 순환 참조
    // ==========================================
    @Component
    static class CtorA {
        private final CtorB b;
        public CtorA(CtorB b) { this.b = b; } // A를 만들려면 B가 필요!
    }
    @Component
    static class CtorB {
        private final CtorA a;
        public CtorB(CtorA a) { this.a = a; } // B를 만들려면 A가 필요! (닭과 달걀)
    }


    // ==========================================
    // 시나리오 2: 필드 주입에서의 순환 참조
    // ==========================================
    @Component
    static class FieldA {
        @Autowired private FieldB b; // 껍데기를 먼저 만들 수 있음!
        public String helloA() { return "Hello from A"; }
    }
    @Component
    static class FieldB {
        @Autowired private FieldA a;
        public String helloB() { return "Hello from B -> " + a.helloA(); }
    }


    public static void main(String[] args) {
        int failures = 0;

        System.out.println("=== 시나리오 1: 생성자 주입 순환 참조 ===");
        try {
            var ctx = new AnnotationConfigApplicationContext();
            ctx.register(CtorA.class, CtorB.class); // 빈 2개 수동 등록
            ctx.refresh(); // 조립 시작!
            System.out.println("  통과? (여기 도달하면 안 됨)");
            ctx.close();
        } catch (Exception e) {
            failures++;
            System.out.println("  [부팅 실패!] 예외 발생: " + e.getClass().getSimpleName());
            System.out.println("  원인: " + extractRootCause(e));
        }

        System.out.println("\n===============================================");

        System.out.println("\n=== 시나리오 2: 옛날 스프링(allow=true) 필드 주입 ===");
        try {
            var ctx = new AnnotationConfigApplicationContext();
            ctx.register(FieldA.class, FieldB.class);
            ctx.refresh();
            System.out.println("  [부팅 성공!] 빈 껍데기만 먼저 만들고 나중에 꽂아줘서 통과함.");
            FieldB b = ctx.getBean(FieldB.class);
            System.out.println("  실제 호출해보기: " + b.helloB());
            ctx.close();
        } catch (Exception e) {
            System.out.println("  실패: " + e.getClass().getSimpleName());
        }

        System.out.println("\n===============================================");

        System.out.println("\n=== 시나리오 3: 요즘 스프링 부트(2.6 이상) 필드 주입 ===");
        System.out.println("  (allowCircularReferences = false 상태)");
        try {
            var ctx = new AnnotationConfigApplicationContext();
            // 스프링 부트 2.6 이상부터는 기본적으로 이 설정이 꺼져 있습니다.
            ctx.getDefaultListableBeanFactory().setAllowCircularReferences(false);
            ctx.register(FieldA.class, FieldB.class);
            ctx.refresh();
            System.out.println("  통과? (여기 도달하면 안 됨)");
            ctx.close();
        } catch (Exception e) {
            failures++;
            System.out.println("  [부팅 실패!] 예외 발생: " + e.getClass().getSimpleName());
            System.out.println("  원인: 스프링 부트 2.6+ 가 막은 동작입니다.");
        }

        MeasurementLog.save("s4-1", "순환 참조 시도 3가지",
            "부팅 실패 " + failures + "건 / 통과 " + (3 - failures) + "건");

        System.out.println("\n[학습 포인트]");
        System.out.println("  - 생성자 주입: A를 만들려니 B가 필요하고, B를 만들려니 A가 필요해서 시작조차 못 하고 즉시 터집니다. (가장 좋은 에러!)");
        System.out.println("  - 필드 주입(과거): 일단 껍데기만 만들어서 통과시켜 주지만, 나중에 멀티스레드 환경에서 NPE(NullPointer)가 터질 위험이 큽니다.");
        System.out.println("  - 필드 주입(요즘): 위와 같은 대형 사고를 막기 위해, 스프링 부트 2.6부터는 필드 주입의 순환 참조도 부팅할 때 싹 다 막아버립니다.");
    }

    private static String extractRootCause(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}