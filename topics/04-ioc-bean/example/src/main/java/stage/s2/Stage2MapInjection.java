package stage.s2;

import domain.NotificationSender;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * STAGE 2-4 보너스: Map<String, NotificationSender> 자동 주입 — Strategy 패턴의 우아한 구현.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>Map 키 = Bean 이름 (@Component("email") → 키 "email")</li>
 *   <li>@Component (이름 생략) 이면 키가 클래스명 camelCase ("emailSender")</li>
 *   <li>새 sender 추가 = Map 자동 확장. 라우터 코드 수정 X (OCP)</li>
 * </ul>
 *
 * <h3>실행</h3>
 * <pre>
 * ./gradlew run -PmainClass=stage.Stage2MapInjection
 * </pre>
 */
public class Stage2MapInjection {

    /** Map 으로 모든 sender 받아서 channel 로 분기 */
    static class DispatcherService {
        private final Map<String, NotificationSender> senders;

        public DispatcherService(Map<String, NotificationSender> senders) {
            this.senders = senders;
            System.out.println("[DispatcherService] 주입받은 sender 키: " + senders.keySet());
        }

        public void send(String channel, String to, String message) {
            NotificationSender sender = senders.get(channel);
            if (sender == null) {
                System.out.println("⚠️ channel=\"" + channel + "\" 에 해당하는 sender 없음. "
                    + "등록된 키: " + senders.keySet());
                return;
            }
            sender.send(to, message);
        }
    }

    @Configuration
    @ComponentScan(basePackages = "domain")
    static class Config {
        @Bean
        public DispatcherService dispatcherService(Map<String, NotificationSender> senders) {
            return new DispatcherService(senders);
        }
    }

    public static void main(String[] args) {
        var ctx = new AnnotationConfigApplicationContext(Config.class);
        DispatcherService dispatcher = ctx.getBean(DispatcherService.class);

        System.out.println("\n=== channel 로 분기 ===");
        dispatcher.send("email", "user@example.com", "이메일 알림");
        dispatcher.send("sms", "010-1234-5678", "SMS 알림");
        dispatcher.send("push", "device-id-1", "푸시 알림");
        dispatcher.send("slack", "#general", "슬랙 알림");

        System.out.println("\n=== 잘못된 키 (Bean 이름이 \"emailSender\" 가 아니라 \"email\") ===");
        dispatcher.send("emailSender", "user@example.com", "이건 안 됨");

        ctx.close();

        System.out.println("\n[학습 포인트]");
        System.out.println("  Map<String, T> 자동 주입 → 키는 Bean 이름");
        System.out.println("  @Component(\"email\") 이면 키가 \"email\"");
        System.out.println("  @Component (이름 생략) 이면 키가 클래스명 camelCase (\"emailSender\")");
        System.out.println("  → 새 sender 추가 시 DispatcherService 코드 수정 X — Strategy 패턴 + OCP");
    }
}
