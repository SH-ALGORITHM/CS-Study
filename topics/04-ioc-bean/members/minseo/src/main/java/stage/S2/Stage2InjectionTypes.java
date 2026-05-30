package stage.S2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * STAGE 2-3: 의존성 주입 3가지 방식 비교 (생성자 / 필드 / 세터)
 */
public class Stage2InjectionTypes {

    // 주입 대상이 될 더미 의존성 객체
    static class DummyDependency {
        public DummyDependency() {
            System.out.println("  [DummyDependency] 생성자 호출됨");
        }
        public String hello() { return "hello from dependency!"; }
    }

    /** 방법 A — 생성자 주입 (가장 권장됨!) */
    static class ConstructorInjected {
        private final DummyDependency dep;   // 핵심: final 사용 가능

        public ConstructorInjected(DummyDependency dep) {
            this.dep = dep;
            System.out.println("  [ConstructorInjected] 생성자 호출 시점 — dep 이미 주입됨 (null 아님? "
                + (dep != null) + ")");
        }
        public void use() { System.out.println("  사용: " + dep.hello()); }
    }

    /** 방법 B — 필드 주입 (비권장) */
    static class FieldInjected {
        @Autowired
        private DummyDependency dep;   // final 사용 불가

        public FieldInjected() {
            System.out.println("  [FieldInjected] 생성자 호출 시점 — dep 아직 주입 안됨 (null? "
                + (dep == null) + ")");
        }
        public void use() { System.out.println("  사용: " + dep.hello()); }
    }

    /** 방법 C — 세터 주입 (선택적일 때만) */
    static class SetterInjected {
        private DummyDependency dep; // final 사용 불가

        public SetterInjected() {
            System.out.println("  [SetterInjected] 생성자 호출 시점 — dep 아직 주입 안됨 (null? "
                + (dep == null) + ")");
        }
        
        @Autowired
        public void setDep(DummyDependency dep) {
            System.out.println("  [SetterInjected] setDep() 세터 호출됨 — 주입 완료");
            this.dep = dep;
        }
        public void use() { System.out.println("  사용: " + dep.hello()); }
    }

    // 내부 빈 설정 (테스트용)
    @Configuration
    static class Config {
        @Bean
        public DummyDependency dependency() { return new DummyDependency(); }

        @Bean
        public ConstructorInjected constructorInjected(DummyDependency dep) {
            return new ConstructorInjected(dep);
        }

        @Bean
        public FieldInjected fieldInjected() { return new FieldInjected(); }

        @Bean
        public SetterInjected setterInjected() { return new SetterInjected(); }
    }

    public static void main(String[] args) {
        var ctx = new AnnotationConfigApplicationContext(Config.class);

        System.out.println("\n=== [1] 생성자 주입 확인 ===");
        ctx.getBean(ConstructorInjected.class).use();

        System.out.println("\n=== [2] 필드 주입 확인 ===");
        ctx.getBean(FieldInjected.class).use();

        System.out.println("\n=== [3] 세터 주입 확인 ===");
        ctx.getBean(SetterInjected.class).use();

        ctx.close();

        System.out.println("\n[학습 포인트]");
        System.out.println("  - 생성자 주입: 불변성(final) 보장, 객체 생성 시점에 의존성 완벽히 준비됨.");
        System.out.println("  - 필드 주입  : 껍데기 먼저 생성 후 리플렉션으로 주입. 테스트하기 매우 까다로움.");
        System.out.println("  - 세터 주입  : 껍데기 생성 후 세터 메서드 호출. 누군가 런타임에 의존성을 바꿀 위험 존재.");
    }
}