package stage.S3;

import infra.MeasurementLog;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * STAGE 3-3: 싱글톤 vs 프로토타입 — 1000 회 getBean() 호출 시 생성자 카운트.
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
        @Scope("prototype") // 이 어노테이션이 핵심입니다!
        public PrototypeBean prototypeBean() { return new PrototypeBean(); }
    }

    public static void main(String[] args) {
        var ctx = new AnnotationConfigApplicationContext(Config.class);

        int iterations = 1000;
        for (int i = 0; i < iterations; i++) {
            // 빈을 1000번씩 달라고 요청합니다.
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
        System.out.println("  - 싱글톤: 스프링이 처음에 딱 1개만 만들어서 메모리에 들고 있다가, 달라고 할 때마다 그 1개를 계속 줍니다. (메모리 절약, Stateless해야 함)");
        System.out.println("  - 프로토타입: 스프링이 달라고 할 때마다 매번 새로(new) 만들어서 줍니다. (GC 부담 증가, 상태를 가져야 할 때만 아주 가끔 사용)");
    }
}