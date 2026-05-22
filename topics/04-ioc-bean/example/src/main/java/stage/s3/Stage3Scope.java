package stage.s3;

import infra.MeasurementLog;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * STAGE 3-3: 싱글톤 vs 프로토타입 — 1000 회 getBean() 호출 시 생성자 카운트.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>싱글톤: 1000 회 호출 → 생성자 1 회 (같은 인스턴스 반복 반환)</li>
 *   <li>프로토타입: 1000 회 호출 → 생성자 1000 회 (매번 새 인스턴스)</li>
 * </ul>
 *
 * <h3>실행</h3>
 * <pre>
 * ./gradlew run -PmainClass=stage.Stage3Scope
 * </pre>
 */
public class Stage3Scope {

    static class SingletonBean {
        static final AtomicInteger COUNTER = new AtomicInteger();
        public SingletonBean() { COUNTER.incrementAndGet(); }
    }

    static class PrototypeBean {
        static final AtomicInteger COUNTER = new AtomicInteger();
        public PrototypeBean() { COUNTER.incrementAndGet(); }
    }

    @Configuration
    static class Config {
        @Bean
        public SingletonBean singletonBean() { return new SingletonBean(); }

        @Bean
        @Scope("prototype")
        public PrototypeBean prototypeBean() { return new PrototypeBean(); }
    }

    public static void main(String[] args) {
        var ctx = new AnnotationConfigApplicationContext(Config.class);

        int iterations = 1000;
        for (int i = 0; i < iterations; i++) {
            ctx.getBean(SingletonBean.class);
            ctx.getBean(PrototypeBean.class);
        }

        int singletonCount = SingletonBean.COUNTER.get();
        int prototypeCount = PrototypeBean.COUNTER.get();

        System.out.println("\n=== " + iterations + " 회 ctx.getBean() 호출 결과 ===");
        System.out.println("싱글톤 생성자 호출 수: " + singletonCount + " (1 회만)");
        System.out.println("프로토타입 생성자 호출 수: " + prototypeCount + " (매번)");

        MeasurementLog.save("s3-3", "싱글톤 " + iterations + "회 getBean",
            "생성자 호출 " + singletonCount + "회");
        MeasurementLog.save("s3-3", "프로토타입 " + iterations + "회 getBean",
            "생성자 호출 " + prototypeCount + "회");

        ctx.close();

        System.out.println("\n[학습 포인트]");
        System.out.println("  싱글톤  — 컨테이너 메모리에 1 인스턴스 캐싱 (Stateless 일 때 안전)");
        System.out.println("  프로토타입 — 매번 new. 호출 수만큼 GC 부담 증가");
        System.out.println("  → 기본 싱글톤. 상태 가진 Bean / 짧은 라이프사이클 필요 시만 프로토타입");
    }
}
