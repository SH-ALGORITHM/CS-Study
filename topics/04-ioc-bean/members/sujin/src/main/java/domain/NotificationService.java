package domain;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final NotificationSender sender;

    public NotificationService(@Qualifier("email") NotificationSender sender) {
        System.out.println("[3] NotificationService constructor injection");
        this.sender = sender;
    }

    @PostConstruct
    public void init() {
        System.out.println("[4] NotificationService @PostConstruct");
    }

    public void notify(String to, String message) {
        sender.send(to, message);
    }

    @PreDestroy
    public void destroy() {
        System.out.println("[close] NotificationService @PreDestroy");
    }
}
