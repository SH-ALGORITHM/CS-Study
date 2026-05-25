package stage.s1;

import domain.NotificationSender;
import domain.NotificationService;
import java.util.Arrays;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

public class Stage1Lifecycle {

    @Configuration
    @ComponentScan(basePackages = {"domain", "stage.s1"})
    static class Stage1Config {
    }

    public static void main(String[] args) {
        System.out.println("=== STAGE 1: Notification IoC container ===");

        var context = new AnnotationConfigApplicationContext(Stage1Config.class);

        System.out.println();
        System.out.println("Bean count = " + context.getBeanDefinitionCount());
        Arrays.stream(context.getBeanDefinitionNames())
            .filter(name -> name.contains("Sender")
                || name.contains("Service")
                || name.contains("email")
                || name.contains("lifecycle")
                || name.contains("prototype"))
            .forEach(name -> System.out.println(" - bean: " + name));

        System.out.println();
        System.out.println("=== 1-1. Bean lifecycle order ===");
        var sampleBean = context.getBean(LifecycleSampleBean.class);
        sampleBean.use();

        System.out.println();
        System.out.println("=== Notification domain singleton check ===");
        var service1 = context.getBean(NotificationService.class);
        var service2 = context.getBean(NotificationService.class);
        System.out.println("NotificationService singleton? " + (service1 == service2));

        var sender1 = context.getBean(NotificationSender.class);
        var sender2 = context.getBean(NotificationSender.class);
        System.out.println("NotificationSender singleton? " + (sender1 == sender2));

        service1.notify("sujin@example.com", "STAGE 1 lifecycle check");

        System.out.println();
        System.out.println("=== Prototype scope check ===");
        var trace1 = context.getBean(PrototypeDeliveryTrace.class);
        var trace2 = context.getBean(PrototypeDeliveryTrace.class);
        System.out.printf("trace1.id=%d, trace2.id=%d%n", trace1.id(), trace2.id());
        System.out.println("PrototypeDeliveryTrace singleton? " + (trace1 == trace2));

        System.out.println();
        System.out.println("=== context.close() ===");
        /*
         * 직접 만든 ApplicationContext는 사용 후 close()로 종료해야 한다.
         *
         * close() 시 Spring은 싱글톤 Bean을 정리하면서:
         * - @PreDestroy 메서드 호출
         * - DisposableBean.destroy() 호출
         * - @Bean(destroyMethod = "...")에 지정한 메서드 호출
         *
         * 프로토타입 Bean은 생성/주입/초기화까지만 컨테이너가 관리하므로
         * close() 시 @PreDestroy가 호출되지 않는다.
         */
        context.close();
        System.out.println("PrototypeDeliveryTrace @PreDestroy is not called.");
    }
}
