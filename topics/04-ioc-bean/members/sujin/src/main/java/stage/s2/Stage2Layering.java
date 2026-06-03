package stage.s2;

import domain.NotificationLog;
import domain.NotificationLogRepository;
import domain.NotificationService;
import java.util.Arrays;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * STAGE 2-2. 계층 분리 시연.
 *
 *  - SchemaBootstrap @PostConstruct → notification_log 준비
 *  - NotificationService.notify() → sender.send() + logRepository.save() 협력
 *  - 같은 row 2건 저장 후 count() 로 확인
 *
 * scanBasePackages 를 "domain", "infra" 로 좁혀서 학습에 필요한 Bean 만 띄운다.
 * RedisAutoConfiguration / HibernateJpa 는 4주차 학습 대상이 아니므로 exclude.
 */
public class Stage2Layering {

    /*
     * build.gradle 의 spring-boot-starter-data-jpa 가 들어와 있어서
     * HibernateJpaAutoConfiguration 만 빼면 JpaRepositoriesAutoConfiguration 이
     * 여전히 entityManagerFactory 를 찾으려 시도하다 부팅 실패한다.
     * → JPA 관련 자동 설정 묶음 + Redis Repositories 자동 설정까지 함께 제외.
     */
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
        System.setProperty("spring.devtools.livereload.enabled", "false");
        System.setProperty("spring.devtools.add-properties", "false");

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(Stage2LayeringApplication.class)
            .web(WebApplicationType.NONE)
            .properties(
                "spring.main.banner-mode=off",
                "spring.main.log-startup-info=false"
            )
            .run(args)) {

            System.out.println();
            System.out.println("=== STAGE 2-2. 계층 분리 시연 ===");

            System.out.println("Domain / infra bean 목록:");
            Arrays.stream(context.getBeanDefinitionNames())
                .filter(name -> name.equals("email")
                    || name.equals("notificationService")
                    || name.equals("notificationLogRepository")
                    || name.equals("schemaBootstrap")
                    || name.equals("dataSourceConfig")
                    || name.equals("dataSource"))
                .sorted()
                .forEach(name -> System.out.println(" - " + name));

            NotificationService service = context.getBean(NotificationService.class);
            NotificationLogRepository repository = context.getBean(NotificationLogRepository.class);

            System.out.println();
            System.out.println("--- notify #1 ---");
            NotificationLog saved1 = service.notify("alice@example.com", "Hello via Email");
            System.out.println("saved id=" + saved1.id()
                + ", channel=" + saved1.channel()
                + ", sentAt=" + saved1.sentAt());

            System.out.println();
            System.out.println("--- notify #2 ---");
            NotificationLog saved2 = service.notify("bob@example.com", "Second message");
            System.out.println("saved id=" + saved2.id()
                + ", channel=" + saved2.channel()
                + ", sentAt=" + saved2.sentAt());

            System.out.println();
            System.out.println("notification_log row count = " + repository.count());
            System.out.println("DataSource type 주입 확인  = " + repository.dataSourceType());

            System.out.println();
            System.out.println("=== context.close() — @PreDestroy 확인 ===");
        }
    }
}
