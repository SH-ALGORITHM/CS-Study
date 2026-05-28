package stage.S3;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * STAGE 3-1 (C): SpringApplication.run() 부팅 시간 — Spring Boot 자동 설정 포함.
 */
@SpringBootApplication(scanBasePackages = "domain")
public class Stage3_C_Boot {

    public static void main(String[] args) {
        // 불필요한 로그 끄기
        System.setProperty("spring.main.log-startup-info", "false");
        System.setProperty("spring.main.banner-mode", "off");
        
        long t1 = System.nanoTime();
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_C_Boot.class, args);
        long elapsed = (System.nanoTime() - t1) / 1_000_000;

        int beanCount = ctx.getBeanDefinitionCount();

        System.out.println("\n=== SpringApplication.run() 부팅 ===");
        System.out.println("부팅 시간: " + elapsed + "ms");
        System.out.println("등록된 Bean 수: " + beanCount);

        MeasurementLog.save("s3-1", "SpringApplication.run()",
            "부팅 시간 " + elapsed + "ms / Bean " + beanCount + "개");

        ctx.close();
    }
}