package stage.s1;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * STAGE 1-1: Bean 라이프사이클 관찰.
 *
 * 알림 도메인은 안 쓰고 단순 SampleBean / PrototypeBean 으로 컨테이너 동작 자체에 집중.
 *
 * <h3>관찰 포인트</h3>
 * <ol>
 *   <li>싱글톤: 생성자 → @PostConstruct → 사용 → ctx.close() → @PreDestroy</li>
 *   <li>프로토타입: getBean() 마다 새 인스턴스 + @PreDestroy 호출 안 됨</li>
 * </ol>
 *
 * <h3>실행</h3>
 * <pre>
 * ./gradlew run -PmainClass=stage.Stage1Lifecycle
 * </pre>
 */
public class Stage1Lifecycle {

    @Component
    static class SampleBean {
        public SampleBean() {
            System.out.println("  [1] SampleBean 생성자 호출");
        }
        @PostConstruct
        public void init() {
            System.out.println("  [2] SampleBean @PostConstruct");
        }
        @PreDestroy
        public void destroy() {
            System.out.println("  [4] SampleBean @PreDestroy");
        }
    }

    @Component
    @Scope("prototype")
    static class PrototypeBean {
        public PrototypeBean() {
            System.out.println("  [생성] PrototypeBean 생성자");
        }
        @PostConstruct
        public void init() {
            System.out.println("  [생성] PrototypeBean @PostConstruct");
        }
        @PreDestroy
        public void destroy() {
            System.out.println("  ⚠️ PrototypeBean @PreDestroy — 호출되면 안 됨");
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 싱글톤 Bean 라이프사이클 ===");
        var ctx = new AnnotationConfigApplicationContext();
        ctx.register(SampleBean.class);
        ctx.refresh();

        SampleBean bean = ctx.getBean(SampleBean.class);
        System.out.println("  [3] getBean() 후 사용 — " + bean.getClass().getSimpleName());

        ctx.close();
        System.out.println("싱글톤: close() 호출 시 @PreDestroy 호출 확인\n");

        System.out.println("=== 프로토타입 Bean 라이프사이클 ===");
        var ctx2 = new AnnotationConfigApplicationContext();
        ctx2.register(PrototypeBean.class);
        ctx2.refresh();

        PrototypeBean p1 = ctx2.getBean(PrototypeBean.class);
        PrototypeBean p2 = ctx2.getBean(PrototypeBean.class);
        System.out.println("  p1 == p2 ? " + (p1 == p2) + "  (프로토타입은 매번 새 인스턴스)");

        ctx2.close();
        System.out.println("프로토타입: close() 호출 후에도 @PreDestroy 안 찍힘");
        System.out.println("→ 컨테이너는 프로토타입의 생성까지만 책임짐. 소멸은 GC 가 처리.");
    }
}
