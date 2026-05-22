package stage.s2;

import domain.NotificationSender;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * STAGE 2-5: @Primary 와 @Qualifier 우선순위 충돌.
 *
 * <h3>충돌 시나리오</h3>
 * <ul>
 *   <li>EmailSender 에 @Primary 붙어있음 (= "기본값")</li>
 *   <li>SmsOnlyService 는 @Qualifier("sms") 로 SmsSender 명시</li>
 *   <li>→ @Qualifier 가 이김 (실제 주입된 sender 는 SmsSender)</li>
 * </ul>
 *
 * <h3>실행</h3>
 * <pre>
 * ./gradlew run -PmainClass=stage.Stage2PrimaryConflict
 * </pre>
 */
public class Stage2PrimaryConflict {

    /** SmsSender 를 @Qualifier 로 명시 주입받는 Service */
    static class SmsOnlyService {
        private final NotificationSender sender;
        public SmsOnlyService(NotificationSender sender) {
            this.sender = sender;
        }
        public NotificationSender getSender() { return sender; }
    }

    /** @Qualifier 없이 자동 주입받는 Service (→ @Primary 가 작동) */
    static class AutoService {
        private final NotificationSender sender;
        public AutoService(NotificationSender sender) {
            this.sender = sender;
        }
        public NotificationSender getSender() { return sender; }
    }

    @Configuration
    @ComponentScan(basePackages = "domain")
    static class Config {
        @Bean
        public SmsOnlyService smsOnlyService(@Qualifier("sms") NotificationSender sender) {
            return new SmsOnlyService(sender);
        }

        @Bean
        public AutoService autoService(NotificationSender sender) {
            return new AutoService(sender);
        }
    }

    public static void main(String[] args) {
        var ctx = new AnnotationConfigApplicationContext(Config.class);

        System.out.println("\n=== 충돌 시나리오 ===");
        System.out.println("  EmailSender 에 @Primary 붙어있음");
        System.out.println("  SmsOnlyService 는 @Qualifier(\"sms\") 명시");
        System.out.println("  AutoService 는 @Qualifier 없음 (자동)\n");

        SmsOnlyService smsOnly = ctx.getBean(SmsOnlyService.class);
        System.out.println("SmsOnlyService 가 받은 sender: "
            + smsOnly.getSender().getClass().getSimpleName() + "  ← @Qualifier 가 이김");

        AutoService auto = ctx.getBean(AutoService.class);
        System.out.println("AutoService 가 받은 sender:    "
            + auto.getSender().getClass().getSimpleName() + "  ← @Primary 가 작동");

        ctx.close();

        System.out.println("\n[학습 포인트]");
        System.out.println("  @Primary : 같은 타입 여러 개 중 \"기본값\". @Qualifier 없을 때만 작동");
        System.out.println("  @Qualifier: 명시 지정. 항상 우선");
        System.out.println("  → @Primary 는 폴백, @Qualifier 는 핀포인트");
    }
}
