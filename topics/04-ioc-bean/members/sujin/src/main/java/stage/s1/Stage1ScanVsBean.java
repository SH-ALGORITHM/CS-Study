package stage.s1;

import domain.EmailSender;
import domain.NotificationSender;
import java.util.Arrays;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

public class Stage1ScanVsBean {

    @Configuration
    @ComponentScan(basePackages = "domain")
    static class ComponentScanConfig {
    }

    @Configuration
    static class ManualBeanConfig {

        @Bean
        public NotificationSender email() {
            return new EmailSender();
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 1-2. @ComponentScan auto registration ===");
        var scanContext = new AnnotationConfigApplicationContext(ComponentScanConfig.class);
        printBeans(scanContext);
        scanContext.getBean(NotificationSender.class)
            .send("sujin@example.com", "@ComponentScan registered this sender");
        scanContext.close();

        System.out.println();
        System.out.println("=== 1-2. @Bean manual registration ===");
        var beanContext = new AnnotationConfigApplicationContext(ManualBeanConfig.class);
        printBeans(beanContext);
        beanContext.getBean(NotificationSender.class)
            .send("sujin@example.com", "@Bean registered this sender");
        beanContext.close();

        System.out.println();
        System.out.println("[summary]");
        System.out.println("@ComponentScan: package 아래 @Component/@Service 등을 자동 등록");
        System.out.println("@Bean: 설정 클래스에서 반환한 객체만 명시 등록");
        System.out.println("외부 라이브러리 객체(DataSource, RedisClient)는 소스에 @Component를 붙일 수 없어서 @Bean이 필요");
    }

    private static void printBeans(AnnotationConfigApplicationContext context) {
        System.out.println("Bean count = " + context.getBeanDefinitionCount());
        Arrays.stream(context.getBeanDefinitionNames())
            .filter(name -> name.contains("email")
                || name.contains("notification")
                || name.contains("Config"))
            .forEach(name -> System.out.println(" - bean: " + name));
    }
}
