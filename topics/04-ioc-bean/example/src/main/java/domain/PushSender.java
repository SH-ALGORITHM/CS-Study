package domain;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/**
 * Push 알림 발송 구현체.
 *
 * Bean 이름 = "push".
 */
@Component("push")
public class PushSender implements NotificationSender {

    public PushSender() {
        System.out.println("[PushSender] 생성자 호출");
    }

    @PostConstruct
    public void init() {
        System.out.println("[PushSender] @PostConstruct");
    }

    @PreDestroy
    public void destroy() {
        System.out.println("[PushSender] @PreDestroy");
    }

    @Override
    public void send(String to, String message) {
        System.out.println("[Push] " + to + " ← " + message);
    }
}
