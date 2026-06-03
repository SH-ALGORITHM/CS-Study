package stage.s1;

import domain.NotificationService;

public class NotificationUseCase {

    private final NotificationService service;

    public NotificationUseCase(NotificationService service) {
        this.service = service;
    }

    public void send(String to, String message) {
        service.notify(to, message);
    }
}
