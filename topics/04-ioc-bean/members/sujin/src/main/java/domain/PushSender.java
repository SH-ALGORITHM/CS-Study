package domain;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/**
 * @Component("push") 처럼 이름을 명시한 케이스 → Bean 이름 = "push".
 * EmailSender 와 같은 정책 (명시 이름) 으로 Map 키가 "push" 가 된다.
 */
@Component("push")
public class PushSender implements NotificationSender {

    public PushSender() {
        System.out.println("[1] PushSender constructor");
    }

    @PostConstruct
    public void init() {
        System.out.println("[2] PushSender @PostConstruct");
    }

    @Override
    public void send(String to, String message) {
        System.out.printf("[use] Push send to=%s, message=%s%n", to, message);
    }

    @PreDestroy
    public void destroy() {
        System.out.println("[close] PushSender @PreDestroy");
    }
}
