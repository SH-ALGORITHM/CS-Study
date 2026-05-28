package stage.s4;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

/**
 * STAGE 4-1 / 4-2. 순환 참조 재현 — 생성자 / 필드 / 세터 3가지 주입 방식 비교.
 *
 * Spring Boot 2.6+ 의 디폴트 동작 (spring.main.allow-circular-references=false) 을
 * AnnotationConfigApplicationContext + setAllowCircularReferences(false) 로 시뮬레이션.
 * → 3가지 주입 방식 모두 부팅 실패.
 *
 * NOTE:
 *  - Spring Framework 자체의 디폴트는 allow-circular-references = true (필드/세터 통과).
 *  - Spring Boot 2.6+ 가 부팅 시점에 false 로 강제 → 모든 주입 방식이 부팅 실패.
 *  - 생성자 순환은 false / true 와 무관하게 항상 실패 (닭-달걀 문제).
 *
 * 인너 @Component 클래스들은 ComponentScan 안 켜면 자동 등록 X → 다른 시연에 영향 없음.
 * 본 main 은 register(Class...) 로 명시 등록.
 */
public class Stage4Circular {

    // ── 생성자 순환 ──
    @Component
    public static class CtorA {
        private final CtorB b;
        public CtorA(CtorB b) {
            System.out.println("[CtorA] constructor — b=" + (b == null ? "null" : b.getClass().getSimpleName()));
            this.b = b;
        }
    }

    @Component
    public static class CtorB {
        private final CtorA a;
        public CtorB(CtorA a) {
            System.out.println("[CtorB] constructor — a=" + (a == null ? "null" : a.getClass().getSimpleName()));
            this.a = a;
        }
    }

    // ── 필드 순환 ──
    @Component
    public static class FieldA {
        @Autowired
        FieldB b;
    }

    @Component
    public static class FieldB {
        @Autowired
        FieldA a;
    }

    // ── 세터 순환 ──
    @Component
    public static class SetterA {
        private SetterB b;
        @Autowired
        public void setB(SetterB b) {
            this.b = b;
        }
    }

    @Component
    public static class SetterB {
        private SetterA a;
        @Autowired
        public void setA(SetterA a) {
            this.a = a;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== STAGE 4. 순환 참조 재현 (allow-circular-references=false — Spring Boot 2.6+) ===");

        runCase("생성자 순환 (A -> B -> A, 생성자 주입)", CtorA.class, CtorB.class);
        runCase("필드 순환 (A -> B -> A, @Autowired 필드)", FieldA.class, FieldB.class);
        runCase("세터 순환 (A -> B -> A, @Autowired 세터)", SetterA.class, SetterB.class);

        System.out.println();
        System.out.println("[결론]");
        System.out.println(" - allow-circular-references=false 에서 3가지 주입 방식 모두 부팅 실패");
        System.out.println(" - 생성자 순환은 메커니즘상 항상 실패 (닭-달걀): A 만들려면 B 필요, B 만들려면 A 필요");
        System.out.println(" - 필드/세터 순환은 Spring 6.x 디폴트 (true) 에선 통과 가능 — Stage4Resolve 의 (c) 케이스에서 시연");
    }

    private static void runCase(String label, Class<?>... beans) {
        System.out.println();
        System.out.println("--- " + label + " ---");
        try (var ctx = new AnnotationConfigApplicationContext()) {
            // Spring Boot 2.6+ 와 동일 — 순환 참조 부팅 시점 차단
            ctx.getDefaultListableBeanFactory().setAllowCircularReferences(false);
            ctx.register(beans);
            ctx.refresh();
            System.out.println("(예상 외) 부팅 성공 — 순환 참조 통과됨");
        } catch (Throwable t) {
            Throwable root = rootCause(t);
            System.out.println("[부팅 실패] " + root.getClass().getSimpleName());
            String msg = root.getMessage();
            if (msg != null) {
                String trimmed = msg.length() > 240 ? msg.substring(0, 240) + "..." : msg;
                System.out.println("  메시지: " + trimmed);
            }
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }
}
