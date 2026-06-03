package stage.s1;

import java.util.Arrays;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class Stage1BootCount {

    @SpringBootApplication(
        scanBasePackages = "stage.s1.boot.empty",
        exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            RedisAutoConfiguration.class
        }
    )
    static class EmptyBootApplication {
    }

    @SpringBootApplication(
        scanBasePackages = "domain",
        exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            RedisAutoConfiguration.class
        }
    )
    static class NotificationBootApplication {
    }

    public static void main(String[] args) {
        System.setProperty("spring.devtools.restart.enabled", "false");
        System.setProperty("spring.devtools.livereload.enabled", "false");
        System.setProperty("spring.devtools.add-properties", "false");

        System.out.println("=== 1-4. Empty @SpringBootApplication ===");
        int emptyCount = runAndCount(EmptyBootApplication.class);

        System.out.println();
        System.out.println("=== 1-4. Notification domain + @SpringBootApplication ===");
        int notificationCount = runAndCount(NotificationBootApplication.class);

        System.out.println();
        System.out.println("[summary]");
        System.out.println("Empty Boot Bean count = " + emptyCount);
        System.out.println("Notification Boot Bean count = " + notificationCount);
        System.out.println("Domain Bean increase = " + (notificationCount - emptyCount));
        System.out.println("@SpringBootApplication adds component scan + auto configuration on top of plain Spring.");
    }

    private static int runAndCount(Class<?> applicationClass) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(applicationClass)
            .web(WebApplicationType.NONE)
            .properties(
                "spring.main.banner-mode=off",
                "spring.main.log-startup-info=false",
                "spring.devtools.restart.enabled=false",
                "spring.devtools.livereload.enabled=false",
                "spring.devtools.add-properties=false"
            )
            .run()) {

            int count = context.getBeanDefinitionCount();
            System.out.println("Bean count = " + count);

            System.out.println("Domain beans:");
            Arrays.stream(context.getBeanDefinitionNames())
                .filter(name -> name.equals("email") || name.equals("notificationService"))
                .forEach(name -> System.out.println(" - " + name));

            System.out.println("Auto-config / infrastructure sample:");
            Arrays.stream(context.getBeanDefinitionNames())
                .filter(name -> name.contains("AutoConfiguration")
                    || name.contains("configurationProperties")
                    || name.contains("applicationTaskExecutor"))
                .limit(12)
                .forEach(name -> System.out.println(" - " + name));

            return count;
        }
    }
}
