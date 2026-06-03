package stage.s3;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * STAGE 3-4. @Lazy 전후 부팅 시간.
 *
 * 일부러 무거운 Bean (HeavyBean — 생성자에서 Thread.sleep(2초)) 을 만들고:
 *   1. Eager (디폴트):    부팅 시점에 생성 → 부팅 시간 ~ 2초 추가
 *   2. @Lazy:            부팅 시점에 안 생성 → 부팅 시간 정상 / 첫 getBean 시 2초 지연
 *
 * 면접 단골: "@Lazy 의 부작용은?" → 첫 사용자가 손해. 캐시 워밍업이 별도로 필요할 수도.
 */
public class Stage3Lazy {

    private static final long HEAVY_SLEEP_MS = 2_000;

    static class HeavyBean {
        public HeavyBean() {
            System.out.println("    [HeavyBean] constructor — Thread.sleep(" + HEAVY_SLEEP_MS + "ms) 시작");
            try {
                Thread.sleep(HEAVY_SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("    [HeavyBean] constructor 완료");
        }

        public void use() {
            System.out.println("    [HeavyBean] use() — 실제 사용");
        }
    }

    @Configuration
    static class EagerConfig {
        @Bean
        public HeavyBean heavyBean() {
            return new HeavyBean();
        }
    }

    @Configuration
    static class LazyConfig {
        @Bean
        @Lazy
        public HeavyBean heavyBean() {
            return new HeavyBean();
        }
    }

    public static void main(String[] args) {
        System.out.println("=== STAGE 3-4. @Lazy 전후 부팅 시간 ===");

        // ── Eager ──
        System.out.println();
        System.out.println("--- Case 1. Eager (디폴트) ---");
        long t1 = System.nanoTime();
        try (var ctx = new AnnotationConfigApplicationContext(EagerConfig.class)) {
            long bootEagerMs = (System.nanoTime() - t1) / 1_000_000;
            System.out.println("Eager 부팅 시간 = " + bootEagerMs + " ms (HeavyBean 생성 포함)");

            long u1 = System.nanoTime();
            ctx.getBean(HeavyBean.class).use();
            long useEagerMs = (System.nanoTime() - u1) / 1_000_000;
            System.out.println("Eager 첫 getBean = " + useEagerMs + " ms (이미 부팅 시 생성됨)");
        }

        // ── Lazy ──
        System.out.println();
        System.out.println("--- Case 2. @Lazy ---");
        long t2 = System.nanoTime();
        try (var ctx = new AnnotationConfigApplicationContext(LazyConfig.class)) {
            long bootLazyMs = (System.nanoTime() - t2) / 1_000_000;
            System.out.println("Lazy 부팅 시간 = " + bootLazyMs + " ms (HeavyBean 생성 미발생)");

            System.out.println("--- 첫 getBean 호출 시점 ---");
            long u2 = System.nanoTime();
            ctx.getBean(HeavyBean.class).use();
            long useLazyMs = (System.nanoTime() - u2) / 1_000_000;
            System.out.println("Lazy 첫 getBean = " + useLazyMs + " ms (생성 + sleep " + HEAVY_SLEEP_MS + "ms)");

            System.out.println("--- 두 번째 getBean ---");
            long u3 = System.nanoTime();
            ctx.getBean(HeavyBean.class).use();
            long useLazy2Ms = (System.nanoTime() - u3) / 1_000_000;
            System.out.println("Lazy 두 번째 getBean = " + useLazy2Ms + " ms (이미 생성됨 → 싱글톤 캐시)");
        }

        System.out.println();
        System.out.println("[결론]");
        System.out.println(" - Eager: 부팅 비용 ↑ / 사용 시점 비용 0");
        System.out.println(" - Lazy : 부팅 비용 정상 / 첫 사용자가 비용 흡수 (워밍업 필요할 수 있음)");
        System.out.println(" - 트레이드오프: 일찍 실패 (eager) vs 부팅 빠르게 (lazy)");
    }
}
