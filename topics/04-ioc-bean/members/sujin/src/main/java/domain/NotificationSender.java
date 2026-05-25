package domain;

public interface NotificationSender {

    void send(String to, String message);
}
