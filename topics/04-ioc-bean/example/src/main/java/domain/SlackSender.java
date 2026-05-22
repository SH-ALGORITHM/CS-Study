package domain;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/**
 * Slack 알림 발송 구현체.
 *
 * Bean 이름 = "slack".
 */
@Component("slack")
public class SlackSender implements NotificationSender {

    public SlackSender() {
        System.out.println("[SlackSender] 생성자 호출");
    }

    @PostConstruct
    public void init() {
        System.out.println("[SlackSender] @PostConstruct");
    }

    @PreDestroy
    public void destroy() {
        System.out.println("[SlackSender] @PreDestroy");
    }

    @Override
    public void send(String to, String message) {
        System.out.println("[Slack] " + to + " ← " + message);
    }
}
