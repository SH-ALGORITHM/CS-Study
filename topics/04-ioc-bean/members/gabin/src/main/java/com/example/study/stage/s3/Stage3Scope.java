package com.example.study.stage.s3;

import com.example.study.MeasurementLog;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * STAGE 3-3: singleton과 prototype 생성 횟수 비교.
 */
public class Stage3Scope {

    private static final int ITERATIONS = 1_000;

    @Configuration
    static class AppConfig {
        @Bean
        public CountedBean singletonBean() {
            return new CountedBean("singleton");
        }

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        public CountedBean prototypeBean() {
            return new CountedBean("prototype");
        }
    }

    static class CountedBean {
        static final AtomicInteger singletonCreated = new AtomicInteger();
        static final AtomicInteger prototypeCreated = new AtomicInteger();

        CountedBean(String type) {
            if ("singleton".equals(type)) {
                singletonCreated.incrementAndGet();
            } else {
                prototypeCreated.incrementAndGet();
            }
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            for (int i = 0; i < ITERATIONS; i++) {
                ctx.getBean("singletonBean", CountedBean.class);
                ctx.getBean("prototypeBean", CountedBean.class);
            }

            int singletonCount = CountedBean.singletonCreated.get();
            int prototypeCount = CountedBean.prototypeCreated.get();

            System.out.println("=== STAGE 3-3: scope ===");
            System.out.println("singleton created: " + singletonCount);
            System.out.println("prototype created: " + prototypeCount);

            MeasurementLog.save(
                "s3-3",
                "singleton vs prototype",
                "getBean " + ITERATIONS + "회 / singleton 생성 " + singletonCount
                    + "회 / prototype 생성 " + prototypeCount + "회"
            );
        }
    }
}
