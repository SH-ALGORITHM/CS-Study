package stage.s1;

import domain.NotificationService;
import org.springframework.context.ApplicationContext;

public class NotificationServiceLocator {

    private final ApplicationContext context;

    public NotificationServiceLocator(ApplicationContext context) {
        this.context = context;
    }

    public void send(String to, String message) {
        NotificationService service = context.getBean(NotificationService.class);
        service.notify(to, message);
    }
}
