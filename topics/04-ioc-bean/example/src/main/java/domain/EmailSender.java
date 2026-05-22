package domain;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Email 알림 발송 구현체.
 *
 * Bean 이름 = "email" (@Component 의 value 로 명시).
 * @Primary 로 "기본" 구현체 지정 — @Qualifier 없을 때 자동 선택됨.
 *
 * STAGE 2-5 에서 @Primary 와 @Qualifier 충돌 시 @Qualifier 가 이김.
 */
@Component("email")
@Primary
public class EmailSender implements NotificationSender {

    public EmailSender() {
        System.out.println("[EmailSender] 생성자 호출");
    }

    @PostConstruct
    public void init() {
        System.out.println("[EmailSender] @PostConstruct");
    }

    @PreDestroy
    public void destroy() {
        System.out.println("[EmailSender] @PreDestroy");
    }

    @Override
    public void send(String to, String message) {
        System.out.println("[Email] " + to + " ← " + message);
    }
}
