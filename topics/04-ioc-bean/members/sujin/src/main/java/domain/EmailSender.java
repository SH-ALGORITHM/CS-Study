package domain;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component("email")
public class EmailSender implements NotificationSender {

    public EmailSender() {
        System.out.println("[1] EmailSender constructor");
    }

    @PostConstruct
    public void init() {
        System.out.println("[2] EmailSender @PostConstruct");
    }

    @Override
    public void send(String to, String message) {
        System.out.printf("[use] Email send to=%s, message=%s%n", to, message);
    }

    @PreDestroy
    public void destroy() {
        System.out.println("[close] EmailSender @PreDestroy");
    }
}
