package stage.s4;

import infra.MeasurementLog;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * STAGE 4-3: 순환 참조 해결 — @Lazy / 설계 재검토 (Mediator 분리).
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>해결 1 (@Lazy): 한쪽을 @Lazy 로 → 프록시 주입 → 닭·달걀 회피. 동작은 하지만 설계 냄새</li>
 *   <li>해결 2 (Mediator 분리): A 와 B 의 공통 책임을 제 3 자로 → A → M ← B 구조. 근본 해결</li>
 * </ul>
 *
 * <h3>실행</h3>
 * <pre>
 * ./gradlew run -PmainClass=stage.Stage4Resolve
 * </pre>
 */
public class Stage4Resolve {

    // ============ 해결 1: @Lazy 적용 (생성자 주입에 한쪽만) ============
    @Component
    static class LazyA {
        private final LazyB b;
        public LazyA(@Lazy LazyB b) {
            System.out.println("  [LazyA] 생성자 — b 는 프록시: " + b.getClass().getSimpleName());
            this.b = b;
        }
        public String hello2() { return "A2"; }
        public String useB() { return b.hello2(); }
    }
    @Component
    static class LazyB {
        private final LazyA a;
        public LazyB(LazyA a) {
            System.out.println("  [LazyB] 생성자 — a 이미 주입됨 (실제 LazyA)");
            this.a = a;
        }
        public String hello2() { return "B2"; }
    }

    // ============ 해결 2: 설계 재검토 (공통 책임 Mediator 로 분리) ============
    @Component
    static class Mediator {
        public String shared() { return "Mediator 의 공통 책임"; }
    }
    @Component
    static class CleanA {
        private final Mediator mediator;
        public CleanA(Mediator m) { this.mediator = m; }
        public String hello() { return "A → " + mediator.shared(); }
    }
    @Component
    static class CleanB {
        private final Mediator mediator;
        public CleanB(Mediator m) { this.mediator = m; }
        public String hello() { return "B → " + mediator.shared(); }
    }

    public static void main(String[] args) {
        System.out.println("=== 해결 1: @Lazy 로 한쪽 프록시 주입 ===");
        var ctx1 = new AnnotationConfigApplicationContext();
        ctx1.register(LazyA.class, LazyB.class);
        ctx1.refresh();
        System.out.println("  부팅 성공 — @Lazy 가 닭·달걀 회피");
        LazyA a = ctx1.getBean(LazyA.class);
        System.out.println("  a.useB() = " + a.useB());
        ctx1.close();

        System.out.println("\n=== 해결 2: Mediator 분리 (설계 재검토) ===");
        var ctx2 = new AnnotationConfigApplicationContext();
        ctx2.register(Mediator.class, CleanA.class, CleanB.class);
        ctx2.refresh();
        System.out.println("  부팅 성공 — A 와 B 가 서로 의존 X");
        CleanA cleanA = ctx2.getBean(CleanA.class);
        CleanB cleanB = ctx2.getBean(CleanB.class);
        System.out.println("  " + cleanA.hello());
        System.out.println("  " + cleanB.hello());
        ctx2.close();

        MeasurementLog.save("s4-3", "@Lazy 해결", "부팅 성공 — 프록시 주입");
        MeasurementLog.save("s4-3", "Mediator 분리 해결", "부팅 성공 — A↔B 직결 제거");

        System.out.println("\n[학습 포인트]");
        System.out.println("  해결 1 (@Lazy): 동작하지만 설계 냄새 — 진짜 양방향이 필요한지 다시 생각");
        System.out.println("  해결 2 (재설계): 근본 해결. A↔B 직결 → A → M ← B 매개");
        System.out.println("  실무: 일단 @Lazy 로 막고, PR 리뷰에서 재설계 검토 = 흔한 패턴");
    }
}
