package domain;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/**
 * @Component 이름 미명시 → Bean 이름 = "slackSender" (디폴트).
 * Sms 와 같은 정책. Map 의 키가 "slackSender" 가 된다.
 */
@Component
public class SlackSender implements NotificationSender {

    public SlackSender() {
        System.out.println("[1] SlackSender constructor");
    }

    @PostConstruct
    public void init() {
        System.out.println("[2] SlackSender @PostConstruct");
    }

    @Override
    public void send(String to, String message) {
        System.out.printf("[use] Slack send to=%s, message=%s%n", to, message);
    }

    @PreDestroy
    public void destroy() {
        System.out.println("[close] SlackSender @PreDestroy");
    }
}
