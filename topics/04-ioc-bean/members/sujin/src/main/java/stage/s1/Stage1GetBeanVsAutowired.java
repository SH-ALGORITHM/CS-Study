package stage.s1;

import domain.NotificationService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

public class Stage1GetBeanVsAutowired {

    @Configuration
    @ComponentScan(basePackages = "domain")
    static class Config {
    }

    public static void main(String[] args) {
        var context = new AnnotationConfigApplicationContext(Config.class);

        System.out.println("=== 1-3. A: getBean() direct lookup ===");
        NotificationService service = context.getBean(NotificationService.class);
        service.notify("sujin@example.com", "direct ctx.getBean lookup");

        System.out.println();
        System.out.println("=== 1-3. A-2: Service Locator style ===");
        var locator = new NotificationServiceLocator(context);
        locator.send("sujin@example.com", "lookup hidden inside another class");

        System.out.println();
        System.out.println("=== 1-3. B: constructor injection style ===");
        var injectedService = context.getBean(NotificationService.class);
        var useCase = new NotificationUseCase(injectedService);
        useCase.send("sujin@example.com", "dependency is explicit in constructor");

        context.close();

        System.out.println();
        System.out.println("[summary]");
        System.out.println("getBean(): caller depends on Spring ApplicationContext");
        System.out.println("Service Locator: Spring dependency is hidden inside the class");
        System.out.println("constructor injection: dependency is visible in constructor and easy to replace in tests");
    }
}
