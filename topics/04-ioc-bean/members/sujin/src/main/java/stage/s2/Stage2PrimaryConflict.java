package stage.s2;

import domain.EmailSender;
import domain.NotificationSender;
import domain.SmsSender;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * STAGE 2-5. @Primary vs @Qualifier 우선순위 충돌.
 *
 * 같은 타입 NotificationSender Bean 2개 (email / smsSender) 상태에서 3가지 케이스를 비교:
 *
 *  Case 1. @Primary 만        — email 에 @Primary, Consumer 는 명시 안 함 → email 주입
 *  Case 2. @Qualifier 만      — 어느 쪽도 @Primary 없음, Consumer 가 @Qualifier("smsSender") → smsSender 주입
 *  Case 3. 둘 다              — email 에 @Primary + Consumer 가 @Qualifier("smsSender") → @Qualifier 가 이김 → smsSender 주입
 *
 * @Primary 는 "기본값" 정도의 의미. 명시 지정 (@Qualifier) 이 있으면 그쪽이 우선.
 *
 * NOTE: EmailSender 클래스 자체에는 @Primary 를 영구히 붙이지 않는다.
 *   다른 시나리오 (Stage2Layering 등) 에 영향을 안 주기 위해 @Bean 메서드 단위로만 @Primary 표시.
 */
public class Stage2PrimaryConflict {

    static class Consumer {
        private final NotificationSender sender;
        public Consumer(NotificationSender sender) {
            this.sender = sender;
        }
        public NotificationSender sender() { return sender; }
    }

    // Case 1. @Primary 만 — Consumer 는 @Qualifier 없이 받음
    @Configuration
    static class PrimaryOnlyConfig {
        @Bean("email")
        @Primary
        public NotificationSender emailBean() { return new EmailSender(); }

        @Bean("smsSender")
        public NotificationSender smsBean() { return new SmsSender(); }

        @Bean
        public Consumer consumer(NotificationSender sender) {
            return new Consumer(sender);
        }
    }

    // Case 2. @Qualifier 만 — 어느 sender 에도 @Primary 없음
    @Configuration
    static class QualifierOnlyConfig {
        @Bean("email")
        public NotificationSender emailBean() { return new EmailSender(); }

        @Bean("smsSender")
        public NotificationSender smsBean() { return new SmsSender(); }

        @Bean
        public Consumer consumer(@Qualifier("smsSender") NotificationSender sender) {
            return new Consumer(sender);
        }
    }

    // Case 3. 둘 다 — @Primary on email + Consumer 는 @Qualifier("smsSender")
    @Configuration
    static class BothConfig {
        @Bean("email")
        @Primary
        public NotificationSender emailBean() { return new EmailSender(); }

        @Bean("smsSender")
        public NotificationSender smsBean() { return new SmsSender(); }

        @Bean
        public Consumer consumer(@Qualifier("smsSender") NotificationSender sender) {
            return new Consumer(sender);
        }
    }

    public static void main(String[] args) {
        System.out.println("=== STAGE 2-5. @Primary vs @Qualifier 우선순위 충돌 ===");

        runCase("Case 1. @Primary 만 (email @Primary, Consumer: 명시 X)",
            PrimaryOnlyConfig.class, "EmailSender");

        runCase("Case 2. @Qualifier 만 (email/sms 평등, Consumer: @Qualifier(\"smsSender\"))",
            QualifierOnlyConfig.class, "SmsSender");

        runCase("Case 3. 둘 다 (email @Primary + Consumer: @Qualifier(\"smsSender\"))",
            BothConfig.class, "SmsSender");

        System.out.println();
        System.out.println("[결론]");
        System.out.println(" - @Primary 만 있으면 명시 안 한 의존성에 자동 우선 주입 (Case 1)");
        System.out.println(" - @Qualifier 가 있으면 그것이 가장 우선 (Case 2, 3)");
        System.out.println(" - 둘 다 있어도 @Qualifier 가 @Primary 를 덮어씀 (Case 3)");
        System.out.println(" - 면접 단골: 같은 타입 Bean 다수 + @Primary + @Qualifier 충돌 시 @Qualifier 가 이긴다");
    }

    private static void runCase(String label, Class<?> config, String expected) {
        System.out.println();
        System.out.println("--- " + label + " ---");
        System.out.println("(예상: " + expected + ")");
        try (var ctx = new AnnotationConfigApplicationContext(config)) {
            Consumer consumer = ctx.getBean(Consumer.class);
            String actual = consumer.sender().getClass().getSimpleName();
            System.out.println("실제 주입된 sender = " + actual + (actual.equals(expected) ? " ✔" : " ✘"));
            consumer.sender().send("alice@example.com", label);
        }
    }
}
