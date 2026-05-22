package stage.s4;

import infra.MeasurementLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

/**
 * STAGE 4-1, 4-2: 순환 참조 재현 — 생성자 / 필드 / 세터 비교.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>생성자 순환: A 생성하려면 B 필요, B 생성하려면 A 필요 → 닭·달걀 → 즉시 실패</li>
 *   <li>필드 순환 (allow=true 기본): A 빈 껍데기 → B 빈 껍데기 → 주입 → 통과</li>
 *   <li>필드 순환 + setAllowCircularReferences(false): 부팅 실패 (Spring Boot 2.6+ 기본)</li>
 * </ul>
 *
 * <h3>실행</h3>
 * <pre>
 * ./gradlew run -PmainClass=stage.Stage4Circular
 * </pre>
 */
public class Stage4Circular {

    // ============ 생성자 순환 ============
    @Component
    static class CtorA {
        private final CtorB b;
        public CtorA(CtorB b) { this.b = b; }
    }
    @Component
    static class CtorB {
        private final CtorA a;
        public CtorB(CtorA a) { this.a = a; }
    }

    // ============ 필드 순환 ============
    @Component
    static class FieldA {
        @Autowired private FieldB b;
        public String hello2() { return "A2"; }
    }
    @Component
    static class FieldB {
        @Autowired private FieldA a;
        public String hello() { return "B → " + a.hello2(); }
    }

    public static void main(String[] args) {
        int failures = 0;

        System.out.println("=== 시나리오 1: 생성자 순환 (어떤 설정에서도 실패) ===");
        try {
            var ctx = new AnnotationConfigApplicationContext();
            ctx.register(CtorA.class, CtorB.class);
            ctx.refresh();
            System.out.println("  통과? (여기 도달하면 안 됨)");
            ctx.close();
        } catch (Exception e) {
            failures++;
            System.out.println("  부팅 실패: " + e.getClass().getSimpleName());
            System.out.println("  원인: " + extractRootCause(e));
        }

        System.out.println("\n=== 시나리오 2: 필드 순환 (allow=true 기본) — 통과 ===");
        try {
            var ctx = new AnnotationConfigApplicationContext();
            ctx.register(FieldA.class, FieldB.class);
            ctx.refresh();
            System.out.println("  부팅 성공 — 빈 껍데기 메커니즘으로 통과");
            FieldB b = ctx.getBean(FieldB.class);
            System.out.println("  호출: " + b.hello());
            ctx.close();
        } catch (Exception e) {
            System.out.println("  실패: " + e.getClass().getSimpleName());
        }

        System.out.println("\n=== 시나리오 3: 필드 순환 + setAllowCircularReferences(false) ===");
        System.out.println("  (Spring Boot 2.6+ 의 기본 동작과 동일)");
        try {
            var ctx = new AnnotationConfigApplicationContext();
            ctx.getDefaultListableBeanFactory().setAllowCircularReferences(false);
            ctx.register(FieldA.class, FieldB.class);
            ctx.refresh();
            System.out.println("  통과? (여기 도달하면 안 됨)");
            ctx.close();
        } catch (Exception e) {
            failures++;
            System.out.println("  부팅 실패: " + e.getClass().getSimpleName());
            System.out.println("  → Spring Boot 2.6+ 가 막은 동작");
        }

        MeasurementLog.save("s4-1", "순환 참조 시도 3 가지",
            "부팅 실패 " + failures + "건 / 통과 " + (3 - failures) + "건");

        System.out.println("\n[학습 포인트]");
        System.out.println("  생성자: 닭·달걀 (둘 다 못 만듦) → 항상 실패");
        System.out.println("  필드/세터 (allow=true): 빈 껍데기 만든 후 주입 → 통과 (단 불완전 상태로 호출 시 NPE)");
        System.out.println("  Spring Boot 2.6+ (allow=false 기본): 빈 껍데기 메커니즘 차단 → 모두 실패");
        System.out.println("  → 2.6+ 가 막은 이유: 다른 스레드가 초기화 미완성 인스턴스 사용 → NPE / 부분 동작");
        System.out.println("     본 시연은 단일 스레드라 통과해 보이지만, 실제 운영에서는 race 로 터짐");
    }

    private static String extractRootCause(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
