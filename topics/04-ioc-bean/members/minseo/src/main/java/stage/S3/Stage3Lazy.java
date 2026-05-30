package stage.S3;

import infra.MeasurementLog;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * STAGE 3-4: @Lazy 전후 부팅 시간 비교 — 무거운 Bean (생성자 2 초 sleep) 사용.
 */
public class Stage3Lazy {

    // 만들어지는 데 무려 2초나 걸리는 아주 뚱뚱한 빈입니다.
    static class HeavyBean {
        public HeavyBean() {
            System.out.println("  [HeavyBean] 생성자 — 2 초 sleep 시작...");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("  [HeavyBean] 생성 완료!");
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
        @Lazy // 핵심! 스프링에게 "부팅할 때 만들지 말고, 진짜 필요할 때까지 미뤄!" 라고 지시합니다.
        public HeavyBean heavyBean() { return new HeavyBean(); }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. Eager (@Lazy 없음) ===");
        long t1 = System.nanoTime();
        var ctx1 = new AnnotationConfigApplicationContext(EagerConfig.class); // 이때 만들어집니다.
        long bootEager = (System.nanoTime() - t1) / 1_000_000;
        System.out.println("부팅 시간: " + bootEager + "ms (HeavyBean 2 초 포함되어 느림)");
        ctx1.close();

        System.out.println("\n=================================");

        System.out.println("\n=== 2. Lazy (@Lazy 적용) ===");
        long t2 = System.nanoTime();
        var ctx2 = new AnnotationConfigApplicationContext(LazyConfig.class); // 이때 안 만듭니다!
        long bootLazy = (System.nanoTime() - t2) / 1_000_000;
        System.out.println("부팅 시간: " + bootLazy + "ms (HeavyBean 생성 안 해서 빠름)");

        System.out.println("\n[첫 getBean() 호출]");
        long t3 = System.nanoTime();
        HeavyBean bean = ctx2.getBean(HeavyBean.class); // 지각생 HeavyBean이 드디어 이때 만들어집니다.
        long firstCall = (System.nanoTime() - t3) / 1_000_000;
        System.out.println("getBean() 시간: " + firstCall + "ms (이 시점에 생성되느라 2초 지연 발생!)");

        bean.use();
        ctx2.close();

        MeasurementLog.save("s3-4", "Eager (@Lazy 없음)",
            "부팅 시간 " + bootEager + "ms");
        MeasurementLog.save("s3-4", "Lazy (@Lazy 적용)",
            "부팅 " + bootLazy + "ms / 첫 호출 " + firstCall + "ms");

        System.out.println("\n[학습 포인트]");
        System.out.println("  - @Lazy를 안 쓰면: 서버가 켜질 때 모든 객체를 다 만드느라 부팅이 엄청 느려집니다.");
        System.out.println("  - @Lazy를 쓰면: 서버 켜질 때는 안 만들어서 부팅은 빠르지만, 첫 번째 접속한 고객이 저 무거운 객체가 만들어지는 2초를 덤터기 써야 합니다.");
        System.out.println("  - 운영 트레이드오프: 부팅 시간을 단축할 것인가 vs 첫 고객의 UX 지연을 막을 것인가?");
    }
}