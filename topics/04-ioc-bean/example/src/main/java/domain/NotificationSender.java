package domain;

/**
 * 알림 발송 인터페이스 — STAGE 2-4 / 2-5 의 다형성 학습 베이스.
 *
 * 구현체 4 개 (Email / SMS / Push / Slack) 가 모두 이 인터페이스를 구현.
 * NotificationService 는 @Qualifier 또는 Map<String, NotificationSender> 로 주입받음.
 */
public interface NotificationSender {

    void send(String to, String message);
}
