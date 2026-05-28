package domain;

/**
 * 알림 전송 전략 인터페이스.
 *
 * 구현체 (Email/Sms/Push/Slack 등) 가 늘어도 NotificationService 는
 * 이 인터페이스만 알면 된다. — DIP (의존성 역전 원칙) 적용.
 *
 * channel() 은 NotificationLog 저장 시 어느 채널로 보냈는지를 식별하는 용도.
 * default 구현은 클래스명에서 "Sender" 를 떼고 소문자로 — Email/Sms/Push/Slack 모두 자연스럽게 동작.
 */
public interface NotificationSender {

    void send(String to, String message);

    default String channel() {
        String name = getClass().getSimpleName();
        if (name.endsWith("Sender")) {
            name = name.substring(0, name.length() - "Sender".length());
        }
        return name.toLowerCase();
    }
}
