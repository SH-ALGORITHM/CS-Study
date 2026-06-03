package stage.s2;

import domain.EmailSender;
import domain.NotificationSender;
import domain.PushSender;
import domain.SlackSender;
import domain.SmsSender;
import java.util.Arrays;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * STAGE 2-4. 다중 구현체 + @Qualifier.
 *
 * 같은 타입 (NotificationSender) Bean 이 4개 등록된 상태에서:
 *  Case A. @Qualifier 없이 타입 1개만 받기 → NoUniqueBeanDefinitionException 재현
 *  Case B. @Qualifier 명시로 특정 sender 지정 → 정상 주입
 *
 * 4개 sender 의 Bean 이름 정책을 다양화해서 ctx.getBeanDefinitionNames() 결과를 비교한다.
 *   - EmailSender:  @Component("email")      → Bean 이름 "email"
 *   - SmsSender:    @Component (디폴트)       → Bean 이름 "smsSender"
 *   - PushSender:   @Component("push")       → Bean 이름 "push"
 *   - SlackSender:  @Component (디폴트)       → Bean 이름 "slackSender"
 */
public class Stage2Qualifier {

    /** 같은 타입 Bean 이 여러 개라 @Qualifier 없이는 부팅 실패한다. */
    @Configuration
    static class AmbiguousConfig {
        @Bean
        public AmbiguousConsumer ambiguousConsumer(NotificationSender sender) {
            return new AmbiguousConsumer(sender);
        }
    }

    static class AmbiguousConsumer {
        private final NotificationSender sender;
        public AmbiguousConsumer(NotificationSender sender) {
            this.sender = sender;
        }
        public NotificationSender sender() { return sender; }
    }

    /** @Qualifier 로 명시 주입 — 동일 타입 다수 중 특정 Bean 이름으로 지정. */
    @Configuration
    static class QualifiedConfig {
        @Bean
        public QualifiedConsumer emailConsumer(@Qualifier("email") NotificationSender sender) {
            return new QualifiedConsumer("emailConsumer", sender);
        }
        @Bean
        public QualifiedConsumer smsConsumer(@Qualifier("smsSender") NotificationSender sender) {
            return new QualifiedConsumer("smsConsumer", sender);
        }
        @Bean
        public QualifiedConsumer pushConsumer(@Qualifier("push") NotificationSender sender) {
            return new QualifiedConsumer("pushConsumer", sender);
        }
        @Bean
        public QualifiedConsumer slackConsumer(@Qualifier("slackSender") NotificationSender sender) {
            return new QualifiedConsumer("slackConsumer", sender);
        }
    }

    static class QualifiedConsumer {
        private final String name;
        private final NotificationSender sender;
        public QualifiedConsumer(String name, NotificationSender sender) {
            this.name = name;
            this.sender = sender;
        }
        public String name() { return name; }
        public NotificationSender sender() { return sender; }
    }

    public static void main(String[] args) {
        System.out.println("=== STAGE 2-4. 다중 구현체 + @Qualifier ===");

        System.out.println();
        System.out.println("--- Case A. @Qualifier 없이 NotificationSender 1개 주입 시도 ---");
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(EmailSender.class, SmsSender.class, PushSender.class, SlackSender.class);
            ctx.register(AmbiguousConfig.class);
            ctx.refresh();
            System.out.println("(예상 외) 부팅 성공 — NoUniqueBeanDefinitionException 안 남");
        } catch (UnsatisfiedDependencyException e) {
            Throwable root = rootCause(e);
            System.out.println("[case A] " + root.getClass().getSimpleName() + ": " + root.getMessage());
        } catch (NoUniqueBeanDefinitionException e) {
            System.out.println("[case A] NoUniqueBeanDefinitionException: " + e.getMessage());
        }

        System.out.println();
        System.out.println("--- Case B. @Qualifier 로 명시 주입 ---");
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(EmailSender.class, SmsSender.class, PushSender.class, SlackSender.class);
            ctx.register(QualifiedConfig.class);
            ctx.refresh();

            System.out.println();
            System.out.println("Bean 이름 목록 (NotificationSender 4개):");
            Arrays.stream(ctx.getBeanDefinitionNames())
                .filter(name -> name.equals("email")
                    || name.equals("smsSender")
                    || name.equals("push")
                    || name.equals("slackSender"))
                .sorted()
                .forEach(name -> System.out.println(" - " + name));

            System.out.println();
            System.out.println("Consumer 가 잡은 sender:");
            for (String beanName : new String[]{"emailConsumer", "smsConsumer", "pushConsumer", "slackConsumer"}) {
                QualifiedConsumer consumer = ctx.getBean(beanName, QualifiedConsumer.class);
                System.out.printf(" - %s → %s%n", consumer.name(), consumer.sender().getClass().getSimpleName());
                consumer.sender().send("alice@example.com", consumer.name() + " test");
            }
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }
}
