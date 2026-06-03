package com.example.study.stage.s3;

import com.example.study.MeasurementLog;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * STAGE 3-4: 무거운 Bean에 @Lazy 적용 전후 비교.
 */
public class Stage3Lazy {

    @Configuration
    static class EagerConfig {
        @Bean
        public HeavyBean eagerHeavyBean() {
            return new HeavyBean();
        }
    }

    @Configuration
    static class LazyConfig {
        @Bean
        @Lazy
        public HeavyBean lazyHeavyBean() {
            return new HeavyBean();
        }
    }

    static class HeavyBean {
        HeavyBean() {
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) {
        long eagerStart = System.nanoTime();
        try (AnnotationConfigApplicationContext ignored =
                 new AnnotationConfigApplicationContext(EagerConfig.class)) {
            // context creation includes HeavyBean creation
        }
        long eagerMs = (System.nanoTime() - eagerStart) / 1_000_000;

        long lazyStart = System.nanoTime();
        try (AnnotationConfigApplicationContext ctx =
                 new AnnotationConfigApplicationContext(LazyConfig.class)) {
            long lazyBootMs = (System.nanoTime() - lazyStart) / 1_000_000;

            long firstGetStart = System.nanoTime();
            ctx.getBean("lazyHeavyBean", HeavyBean.class);
            long firstGetMs = (System.nanoTime() - firstGetStart) / 1_000_000;

            System.out.println("=== STAGE 3-4: @Lazy ===");
            System.out.println("eager boot: " + eagerMs + "ms");
            System.out.println("lazy boot: " + lazyBootMs + "ms");
            System.out.println("lazy first getBean: " + firstGetMs + "ms");

            MeasurementLog.save(
                "s3-4",
                "@Lazy",
                "eager boot " + eagerMs + "ms / lazy boot " + lazyBootMs
                    + "ms / lazy first getBean " + firstGetMs + "ms"
            );
        }
    }
}
