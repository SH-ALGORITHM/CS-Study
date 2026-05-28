package stage.S2;

import domain.AuthRepository;
import domain.AuthService;
import java.util.Arrays;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class Stage2Layering {

    @SpringBootApplication(
        scanBasePackages = {"domain", "infra"},
        exclude = {
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class
        }
    )
    static class Stage2LayeringApplication {
    }

    public static void main(String[] args) {
        System.setProperty("spring.devtools.restart.enabled", "false");

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(Stage2LayeringApplication.class)
            .web(WebApplicationType.NONE)
            .properties("spring.main.log-startup-info=false")
            .run(args)) {

            System.out.println("\n=== STAGE 2-2. 계층 분리 시연 (Auth Domain) ===");

            System.out.println("Domain / infra bean 목록:");
            Arrays.stream(context.getBeanDefinitionNames())
                .filter(name -> name.contains("Auth") || name.contains("auth") || name.equals("dataSource"))
                .sorted()
                .forEach(name -> System.out.println(" - " + name));

            AuthService authService = context.getBean(AuthService.class);
            AuthRepository authRepository = context.getBean(AuthRepository.class);

            System.out.println("\n--- login #1 ---");
            authService.login("minseo", "1234", "jwt-token-abc");

            System.out.println("\n--- login #2 ---");
            authService.login("alice", "pass", "jwt-token-xyz");

            System.out.println("\nDataSource type 주입 확인 = " + authRepository.dataSourceType());

            System.out.println("\n=== context.close() — @PreDestroy 확인 ===");
        }
    }
}