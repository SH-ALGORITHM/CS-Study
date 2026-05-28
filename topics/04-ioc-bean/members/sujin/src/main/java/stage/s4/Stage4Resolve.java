package stage.s4;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * STAGE 4-3. 순환 참조 해결 3가지.
 *
 *  (a) 설계 재검토 — 공통 책임을 제 3 의 서비스 (SharedC) 로 분리. 가장 권장.
 *  (b) @Lazy 한쪽 적용 — Spring 이 프록시 주입으로 순환을 깨준다.
 *  (c) 세터/필드 주입 + allow-circular-references=true — 옛 동작. 비권장.
 *
 * 각 케이스마다 별도 ApplicationContext 를 띄워서 결과 비교.
 *
 * 기준선: baseline 케이스 = 생성자 순환 + allow=false → 부팅 실패 (Stage4Circular 와 동일).
 */
public class Stage4Resolve {

    // ── baseline: 원본 생성자 순환 ──
    @Component public static class OriginalA {
        private final OriginalB b;
        public OriginalA(OriginalB b) { this.b = b; }
    }
    @Component public static class OriginalB {
        private final OriginalA a;
        public OriginalB(OriginalA a) { this.a = a; }
    }

    // ── (a) 설계 재검토: 공통 책임 SharedC 로 분리 ──
    @Component public static class SharedC {
        public String work() { return "shared work"; }
    }
    @Component public static class RedesignedA {
        private final SharedC c;
        public RedesignedA(SharedC c) {
            System.out.println("[RedesignedA] constructor — depends on SharedC");
            this.c = c;
        }
    }
    @Component public static class RedesignedB {
        private final SharedC c;
        public RedesignedB(SharedC c) {
            System.out.println("[RedesignedB] constructor — depends on SharedC");
            this.c = c;
        }
    }

    // ── (b) @Lazy 한쪽 적용 ──
    @Component public static class LazyA {
        private final LazyB b;
        public LazyA(@Lazy LazyB b) {
            System.out.println("[LazyA] constructor — b proxy class=" + b.getClass().getSimpleName());
            this.b = b;
        }
    }
    @Component public static class LazyB {
        private final LazyA a;
        public LazyB(LazyA a) {
            System.out.println("[LazyB] constructor — a=" + a.getClass().getSimpleName());
            this.a = a;
        }
    }

    // ── (c) 세터 + allow-circular-references=true ──
    @Component public static class SetterA {
        private SetterB b;
        @Autowired public void setB(SetterB b) {
            System.out.println("[SetterA] setB — b=" + b.getClass().getSimpleName());
            this.b = b;
        }
    }
    @Component public static class SetterB {
        private SetterA a;
        @Autowired public void setA(SetterA a) {
            System.out.println("[SetterB] setA — a=" + a.getClass().getSimpleName());
            this.a = a;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== STAGE 4-3. 순환 참조 해결 3가지 ===");

        System.out.println();
        System.out.println("--- baseline. 원본 생성자 순환 (allow=false) ---");
        runFail(OriginalA.class, OriginalB.class);

        System.out.println();
        System.out.println("--- (a) 설계 재검토: 공통 SharedC 로 분리 ---");
        runSuccessStrict("Redesigned", RedesignedA.class, RedesignedB.class, SharedC.class);

        System.out.println();
        System.out.println("--- (b) @Lazy 한쪽 적용 (생성자 인자에 @Lazy) ---");
        runSuccessStrict("Lazy", LazyA.class, LazyB.class);

        System.out.println();
        System.out.println("--- (c) 세터 주입 + allow-circular-references=true ---");
        runSuccessAllow("Setter", SetterA.class, SetterB.class);

        System.out.println();
        System.out.println("[결론]");
        System.out.println(" (a) 설계 재검토: 가장 권장 — 의존성 그래프 자체를 깨끗하게 함 (DAG 보장)");
        System.out.println(" (b) @Lazy: 동작하지만 '왜 이 의존성만 lazy?' 라는 코드 냄새 — 임시 회피용");
        System.out.println(" (c) allow-circular-references=true: 옛 코드 호환용. 부팅 안전성 ↓, smell ↑");
        System.out.println(" 면접 답변: 'A → B → A 가 보이면 먼저 SharedC 를 추출해서 A → C ← B 구조로 바꾼다.'");
    }

    private static void runFail(Class<?>... beans) {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.getDefaultListableBeanFactory().setAllowCircularReferences(false);
            ctx.register(beans);
            ctx.refresh();
            System.out.println("(예상 외) baseline 이 부팅 성공");
        } catch (Throwable t) {
            Throwable root = rootCause(t);
            System.out.println("baseline 부팅 실패: " + root.getClass().getSimpleName() + " ✔ (예상대로)");
        }
    }

    private static void runSuccessStrict(String label, Class<?>... beans) {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.getDefaultListableBeanFactory().setAllowCircularReferences(false);
            ctx.register(beans);
            ctx.refresh();
            System.out.println(label + " 부팅 성공 ✔ — allow-circular-references=false 에서도 OK");
            printBeans(ctx, label.toLowerCase());
        } catch (Throwable t) {
            System.out.println("(예상 외) " + label + " 부팅 실패: " + rootCause(t).getClass().getSimpleName());
        }
    }

    private static void runSuccessAllow(String label, Class<?>... beans) {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            // Spring 6.x 디폴트 = true 지만 의도를 명시.
            ctx.getDefaultListableBeanFactory().setAllowCircularReferences(true);
            ctx.register(beans);
            ctx.refresh();
            System.out.println(label + " 부팅 성공 ✔ — allow-circular-references=true 필요 (false 면 실패)");
            printBeans(ctx, label.toLowerCase());
        } catch (Throwable t) {
            System.out.println("(예상 외) " + label + " 부팅 실패: " + rootCause(t).getClass().getSimpleName());
        }
    }

    private static void printBeans(AnnotationConfigApplicationContext ctx, String contains) {
        System.out.println("  Bean 이름:");
        Arrays.stream(ctx.getBeanDefinitionNames())
            .filter(name -> name.toLowerCase().contains(contains) || name.toLowerCase().contains("shared"))
            .sorted()
            .forEach(name -> System.out.println("    - " + name));
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }
}
