package stage.s2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * STAGE 2-3: 생성자 / 필드 / 세터 주입 비교.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>생성자: 생성자 시점에 dep 이미 주입됨 (final 가능)</li>
 *   <li>필드: 생성자에서는 dep null, 컨테이너가 리플렉션으로 나중에 주입</li>
 *   <li>세터: 생성자 후 setDep() 호출됨</li>
 * </ul>
 *
 * <h3>실행</h3>
 * <pre>
 * ./gradlew run -PmainClass=stage.Stage2InjectionTypes
 * </pre>
 */
public class Stage2InjectionTypes {

    static class Dependency {
        public Dependency() {
            System.out.println("  [Dependency] 생성자");
        }
        public String hello() { return "hello"; }
    }

    /** 방법 A — 생성자 주입 (권장) */
    static class ConstructorInjected {
        private final Dependency dep;   // final 가능
        public ConstructorInjected(Dependency dep) {
            this.dep = dep;
            System.out.println("  [ConstructorInjected] 생성자 — dep 이미 주입됨 (null? "
                + (dep == null) + ")");
        }
        public void use() { System.out.println("  사용: " + dep.hello()); }
    }

    /** 방법 B — 필드 주입 */
    static class FieldInjected {
        @Autowired
        private Dependency dep;   // final 불가

        public FieldInjected() {
            System.out.println("  [FieldInjected] 생성자 — dep 아직 null (null? "
                + (dep == null) + ")");
        }
        public void use() { System.out.println("  사용: " + dep.hello()); }
    }

    /** 방법 C — 세터 주입 */
    static class SetterInjected {
        private Dependency dep;

        public SetterInjected() {
            System.out.println("  [SetterInjected] 생성자 — dep 아직 null (null? "
                + (dep == null) + ")");
        }
        @Autowired
        public void setDep(Dependency dep) {
            System.out.println("  [SetterInjected] setDep() 호출 — 주입됨");
            this.dep = dep;
        }
        public void use() { System.out.println("  사용: " + dep.hello()); }
    }

    @Configuration
    static class Config {
        @Bean
        public Dependency dependency() { return new Dependency(); }

        @Bean
        public ConstructorInjected constructorInjected(Dependency dep) {
            return new ConstructorInjected(dep);
        }

        @Bean
        public FieldInjected fieldInjected() { return new FieldInjected(); }

        @Bean
        public SetterInjected setterInjected() { return new SetterInjected(); }
    }

    public static void main(String[] args) {
        var ctx = new AnnotationConfigApplicationContext(Config.class);

        System.out.println("\n=== 생성자 주입 ===");
        ctx.getBean(ConstructorInjected.class).use();

        System.out.println("\n=== 필드 주입 ===");
        ctx.getBean(FieldInjected.class).use();

        System.out.println("\n=== 세터 주입 ===");
        ctx.getBean(SetterInjected.class).use();

        ctx.close();

        System.out.println("\n[학습 포인트]");
        System.out.println("  생성자: final 가능 / 생성자 시점에 주입 완료 / 순환 참조 부팅 시 감지");
        System.out.println("  필드  : final 불가 / 리플렉션 주입 / 테스트 시 ReflectionTestUtils 필요");
        System.out.println("  세터  : final 불가 / 생성자 후 별도 호출 / 선택적 의존성에만 가끔");
        System.out.println("  → 생성자 주입이 표준. 필드는 옛 코드 / 세터는 setter injection 패턴 (드뭄)");
    }
}
