package stage.s3;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * STAGE 3-3. 싱글톤 vs 프로토타입 스코프.
 *
 * 같은 CountingBean 을 두 ApplicationContext 에 각각 다른 스코프로 등록한 뒤
 * ctx.getBean() 을 1000회 호출 → 생성자가 몇 번 호출됐는지 비교.
 *
 *  - 싱글톤:   1000회 호출 → 생성자 1회 (컨테이너당 1 인스턴스)
 *  - 프로토타입: 1000회 호출 → 생성자 1000회 (요청할 때마다 새 인스턴스)
 *
 * CounterBean 의 카운터는 static AtomicInteger → 컨테이너 간 공유.
 * 케이스 사이에 reset() 으로 초기화.
 */
public class Stage3Scope {

    private static final int CALLS = 1000;

    static class CountingBean {
        private static final AtomicInteger COUNTER = new AtomicInteger();

        public CountingBean() {
            COUNTER.incrementAndGet();
        }

        public static int count() {
            return COUNTER.get();
        }

        public static void reset() {
            COUNTER.set(0);
        }
    }

    @Configuration
    static class SingletonConfig {
        @Bean
        @Scope("singleton")
        public CountingBean countingBean() {
            return new CountingBean();
        }
    }

    @Configuration
    static class PrototypeConfig {
        @Bean
        @Scope("prototype")
        public CountingBean countingBean() {
            return new CountingBean();
        }
    }

    public static void main(String[] args) {
        System.out.println("=== STAGE 3-3. 싱글톤 vs 프로토타입 (1000회 ctx.getBean()) ===");

        int singletonCount = runScope("싱글톤", SingletonConfig.class);
        int prototypeCount = runScope("프로토타입", PrototypeConfig.class);

        System.out.println();
        System.out.println("--- 요약 ---");
        System.out.println("싱글톤    1000회 getBean → 생성자 호출 = " + singletonCount);
        System.out.println("프로토타입 1000회 getBean → 생성자 호출 = " + prototypeCount);
        System.out.println();
        System.out.println("싱글톤은 컨테이너 부팅 시점에 1번 생성, 이후 getBean 은 같은 인스턴스 반환.");
        System.out.println("프로토타입은 부팅 시점에 1번 생성 후, getBean 호출마다 새 인스턴스 생성.");
        System.out.println("프로토타입 결과가 1001 인 이유: @Bean 등록 시 (refresh 시) 한 번 + getBean 1000회.");
    }

    private static int runScope(String label, Class<?> config) {
        CountingBean.reset();
        try (var ctx = new AnnotationConfigApplicationContext(config)) {
            System.out.printf("[%s] context refresh 후 카운터 = %d%n", label, CountingBean.count());
            for (int i = 0; i < CALLS; i++) {
                ctx.getBean(CountingBean.class);
            }
            int finalCount = CountingBean.count();
            System.out.printf("[%s] 1000회 getBean 후 카운터 = %d%n", label, finalCount);
            return finalCount;
        }
    }
}
