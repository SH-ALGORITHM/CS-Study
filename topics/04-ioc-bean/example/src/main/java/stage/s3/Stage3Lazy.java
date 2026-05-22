package stage.s3;

import infra.MeasurementLog;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * STAGE 3-4: @Lazy 전후 부팅 시간 비교 — 무거운 Bean (생성자 2 초 sleep) 사용.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>Eager (@Lazy 없음): 부팅 시 HeavyBean 즉시 생성 → 부팅 시간 +2 초</li>
 *   <li>Lazy (@Lazy 적용): 부팅 시 HeavyBean 생성 안 함 → 부팅 빠름. 첫 getBean() 호출 시 +2 초</li>
 * </ul>
 *
 * <h3>실행</h3>
 * <pre>
 * ./gradlew run -PmainClass=stage.Stage3Lazy
 * </pre>
 */
public class Stage3Lazy {

    static class HeavyBean {
        public HeavyBean() {
            System.out.println("  [HeavyBean] 생성자 — 2 초 sleep");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        public void use() { System.out.println("  [HeavyBean] 사용"); }
    }

    @Configuration
    static class EagerConfig {
        @Bean
        public HeavyBean heavyBean() { return new HeavyBean(); }
    }

    @Configuration
    static class LazyConfig {
        @Bean
        @Lazy
        public HeavyBean heavyBean() { return new HeavyBean(); }
    }

    public static void main(String[] args) {
        System.out.println("=== Eager (@Lazy 없음) ===");
        long t1 = System.nanoTime();
        var ctx1 = new AnnotationConfigApplicationContext(EagerConfig.class);
        long bootEager = (System.nanoTime() - t1) / 1_000_000;
        System.out.println("부팅 시간: " + bootEager + "ms (HeavyBean 2 초 포함)");
        ctx1.close();

        System.out.println("\n=== Lazy (@Lazy 적용) ===");
        long t2 = System.nanoTime();
        var ctx2 = new AnnotationConfigApplicationContext(LazyConfig.class);
        long bootLazy = (System.nanoTime() - t2) / 1_000_000;
        System.out.println("부팅 시간: " + bootLazy + "ms (HeavyBean 아직 생성 안 됨)");

        System.out.println("\n첫 getBean() 호출:");
        long t3 = System.nanoTime();
        HeavyBean bean = ctx2.getBean(HeavyBean.class);
        long firstCall = (System.nanoTime() - t3) / 1_000_000;
        System.out.println("getBean() 시간: " + firstCall + "ms (이 시점에 생성자 호출)");

        bean.use();
        ctx2.close();

        MeasurementLog.save("s3-4", "Eager (@Lazy 없음)",
            "부팅 시간 " + bootEager + "ms");
        MeasurementLog.save("s3-4", "Lazy (@Lazy 적용)",
            "부팅 " + bootLazy + "ms / 첫 호출 " + firstCall + "ms");

        System.out.println("\n[학습 포인트]");
        System.out.println("  @Lazy 전: 부팅 시 모든 Bean 생성 → 무거운 Bean 이 부팅 시간 차지");
        System.out.println("  @Lazy 후: 첫 호출 시점까지 생성 미룸 → 부팅 빠름, 첫 사용자 손해");
        System.out.println("  운영 트레이드오프: 부팅 시간 단축 vs 첫 호출 UX 지연");
    }
}
