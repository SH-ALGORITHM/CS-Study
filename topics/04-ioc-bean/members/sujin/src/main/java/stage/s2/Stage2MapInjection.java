package stage.s2;

import domain.EmailSender;
import domain.NotificationSender;
import domain.PushSender;
import domain.SlackSender;
import domain.SmsSender;
import java.util.Arrays;
import java.util.Map;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * STAGE 2-4 (b). Map<String, NotificationSender> 자동 주입.
 *
 * Spring 은 같은 타입 Bean 이 여러 개일 때 Map<BeanName, Bean> 형태로 한 번에 받을 수 있다.
 * 키는 Bean 이름, 값은 Bean 인스턴스.
 *
 * 4개 sender 의 Bean 이름 정책이 다양해서 Map 의 키가 어떻게 결정되는지 직접 확인한다.
 *  - "email"        ← @Component("email") 명시
 *  - "smsSender"    ← @Component 디폴트 (클래스명 camelCase)
 *  - "push"         ← @Component("push") 명시
 *  - "slackSender"  ← @Component 디폴트
 *
 * Dispatcher 는 channel 문자열로 sender 를 골라서 위임한다 — Strategy 패턴 + DI.
 */
public class Stage2MapInjection {

    @Configuration
    static class MapConfig {
        @Bean
        public Dispatcher dispatcher(Map<String, NotificationSender> senders) {
            return new Dispatcher(senders);
        }
    }

    static class Dispatcher {
        private final Map<String, NotificationSender> senders;

        public Dispatcher(Map<String, NotificationSender> senders) {
            System.out.println("[Dispatcher] constructor — Map<String, NotificationSender> 자동 주입");
            System.out.println("[Dispatcher] Map keys = " + senders.keySet());
            this.senders = senders;
        }

        public void dispatch(String channel, String to, String message) {
            NotificationSender sender = senders.get(channel);
            if (sender == null) {
                System.out.printf("[dispatch] channel=%s → 등록된 sender 없음. 사용 가능 = %s%n",
                    channel, senders.keySet());
                return;
            }
            sender.send(to, message);
        }
    }

    public static void main(String[] args) {
        System.out.println("=== STAGE 2-4 (b). Map<String, NotificationSender> 자동 주입 ===");

        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(EmailSender.class, SmsSender.class, PushSender.class, SlackSender.class);
            ctx.register(MapConfig.class);
            ctx.refresh();

            System.out.println();
            System.out.println("ctx.getBeanDefinitionNames() 중 NotificationSender 후보:");
            Arrays.stream(ctx.getBeanDefinitionNames())
                .filter(name -> name.equals("email")
                    || name.equals("smsSender")
                    || name.equals("push")
                    || name.equals("slackSender"))
                .sorted()
                .forEach(name -> System.out.println(" - " + name));

            Dispatcher dispatcher = ctx.getBean(Dispatcher.class);

            System.out.println();
            System.out.println("--- 정상 키 ---");
            dispatcher.dispatch("email", "alice@example.com", "Hello via Email");
            dispatcher.dispatch("smsSender", "010-1111-2222", "Hello via SMS");
            dispatcher.dispatch("push", "device-abc", "Hello via Push");
            dispatcher.dispatch("slackSender", "#general", "Hello via Slack");

            System.out.println();
            System.out.println("--- 잘못된 키 (학습 — @Component 디폴트 이름 못 맞춘 경우) ---");
            dispatcher.dispatch("sms", "010-1111-2222", "Wrong key — Map.get 가 null 반환");
            dispatcher.dispatch("slack", "#general", "Wrong key");
        }
    }
}
