package stage.s3;

import infra.MeasurementLog;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * STAGE 3-1 (B): AnnotationConfigApplicationContext 부팅 시간 — 순수 Spring (Boot 아님).
 *
 * <h3>실행</h3>
 * <pre>
 * ./gradlew run -PmainClass=stage.Stage3_B_Spring
 * </pre>
 */
public class Stage3_B_Spring {

    @Configuration
    @ComponentScan(basePackages = "domain")
    static class Config {}

    public static void main(String[] args) {
        long t1 = System.nanoTime();
        var ctx = new AnnotationConfigApplicationContext(Config.class);
        long elapsed = (System.nanoTime() - t1) / 1_000_000;

        int beanCount = ctx.getBeanDefinitionCount();

        System.out.println("\n=== AnnotationConfigApplicationContext 부팅 ===");
        System.out.println("부팅 시간: " + elapsed + "ms");
        System.out.println("등록된 Bean 수: " + beanCount);

        MeasurementLog.save("s3-1", "AnnotationConfigContext",
            "부팅 시간 " + elapsed + "ms / Bean " + beanCount + "개");

        ctx.close();
    }
}
