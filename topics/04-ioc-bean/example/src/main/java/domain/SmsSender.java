package domain;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/**
 * SMS 알림 발송 구현체.
 *
 * Bean 이름 = "sms".
 * @Primary 없음 — @Qualifier("sms") 명시해야 주입됨.
 */
@Component("sms")
public class SmsSender implements NotificationSender {

    public SmsSender() {
        System.out.println("[SmsSender] 생성자 호출");
    }

    @PostConstruct
    public void init() {
        System.out.println("[SmsSender] @PostConstruct");
    }

    @PreDestroy
    public void destroy() {
        System.out.println("[SmsSender] @PreDestroy");
    }

    @Override
    public void send(String to, String message) {
        System.out.println("[SMS] " + to + " ← " + message);
    }
}
