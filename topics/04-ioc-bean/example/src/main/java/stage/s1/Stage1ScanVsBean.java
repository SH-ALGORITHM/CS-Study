package stage.s1;

import domain.EmailSender;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * STAGE 1-2: @ComponentScan vs @Bean 직접 등록 차이.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>@ComponentScan: basePackage 아래 모든 @Component 자동 등록 (Email/Sms/Push/Slack + NotificationService)</li>
 *   <li>@Bean: 본인이 명시한 객체만 등록. 외부 라이브러리 (HikariDataSource 등) 의 유일한 방법</li>
 * </ul>
 *
 * <h3>실행</h3>
 * <pre>
 * ./gradlew run -PmainClass=stage.Stage1ScanVsBean
 * </pre>
 */
public class Stage1ScanVsBean {

    /** 방법 A — @ComponentScan 자동 등록 */
    @Configuration
    @ComponentScan(basePackages = "domain")
    static class AutoScanConfig {}

    /** 방법 B — @Bean 직접 등록 (EmailSender 1 개만) */
    @Configuration
    static class ManualBeanConfig {
        @Bean
        public EmailSender emailSender() {
            return new EmailSender();
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 방법 A: @ComponentScan (domain 패키지 자동 스캔) ===");
        var ctx1 = new AnnotationConfigApplicationContext(AutoScanConfig.class);
        System.out.println("등록된 Bean 수: " + ctx1.getBeanDefinitionCount());
        for (String name : ctx1.getBeanDefinitionNames()) {
            System.out.println("  - " + name);
        }
        ctx1.close();

        System.out.println("\n=== 방법 B: @Bean 직접 등록 (EmailSender 만) ===");
        var ctx2 = new AnnotationConfigApplicationContext(ManualBeanConfig.class);
        System.out.println("등록된 Bean 수: " + ctx2.getBeanDefinitionCount());
        for (String name : ctx2.getBeanDefinitionNames()) {
            System.out.println("  - " + name);
        }
        ctx2.close();

        System.out.println("\n[학습 포인트]");
        System.out.println("  - @Component 는 본인 코드에만 가능 (외부 라이브러리는 어노테이션 못 붙임)");
        System.out.println("  - 외부 객체 (HikariDataSource / RedisClient) 는 @Bean 으로만 등록 가능");
        System.out.println("  - 방법 A 는 패키지 스캔 비용, 방법 B 는 명시성 — 함께 자주 씀");
    }
}
