package com.example.study.stage.s3;

import com.example.study.MeasurementLog;
import com.example.study.service.OrderService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * STAGE 3-1 B: AnnotationConfigApplicationContext 부팅 시간과 Bean 수 측정.
 */
public class Stage3_B_Spring {

    @Configuration
    @ComponentScan({
        "com.example.study.domain",
        "com.example.study.service"
    })
    static class AppConfig {
    }

    public static void main(String[] args) {
        long start = System.nanoTime();

        try (AnnotationConfigApplicationContext ctx =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            int count = ctx.getBeanDefinitionCount();
            int finalPrice = ctx.getBean(OrderService.class).calculateFinalPrice(50_000);

            System.out.println("=== STAGE 3-B: AnnotationConfigContext ===");
            System.out.println("Bean count: " + count);
            System.out.println("final price: " + finalPrice);
            System.out.println("elapsed: " + elapsedMs + "ms");

            MeasurementLog.save(
                "s3-1",
                "AnnotationConfigContext",
                "Bean " + count + "개 / 최종 금액 " + finalPrice + " / " + elapsedMs + "ms"
            );
        }
    }
}
