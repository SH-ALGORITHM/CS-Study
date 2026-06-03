package domain;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/**
 * @Component 이름을 명시하지 않은 케이스 → Bean 이름은 디폴트 (클래스명 첫 글자 소문자) = "smsSender".
 * EmailSender 의 "email" 과 달라서 Map<String, NotificationSender> 자동 주입의 키 차이를 직접 확인 가능.
 */
@Component
public class SmsSender implements NotificationSender {

    public SmsSender() {
        System.out.println("[1] SmsSender constructor");
    }

    @PostConstruct
    public void init() {
        System.out.println("[2] SmsSender @PostConstruct");
    }

    @Override
    public void send(String to, String message) {
        System.out.printf("[use] Sms send to=%s, message=%s%n", to, message);
    }

    @PreDestroy
    public void destroy() {
        System.out.println("[close] SmsSender @PreDestroy");
    }
}
