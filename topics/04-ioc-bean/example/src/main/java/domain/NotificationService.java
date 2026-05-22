package domain;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 알림 발송 상위 서비스 — STAGE 2-2 (계층 분리) / 2-3 (생성자 주입) / 2-4 (@Qualifier) 학습 베이스.
 *
 * 생성자 주입으로 NotificationSender 받음. @Qualifier("email") 로 4 개 구현체 중 EmailSender 지정.
 *
 * <h3>학습 포인트</h3>
 * <ul>
 *   <li>final 필드 → 생성자 주입만 가능</li>
 *   <li>@Qualifier 가 @Primary 보다 우선 (EmailSender 에 @Primary 있어도 @Qualifier("sms") 면 SmsSender 주입)</li>
 *   <li>주입된 구현체를 본인이 모르고 받음 — 다형성의 핵심</li>
 * </ul>
 */
@Service
public class NotificationService {

    private final NotificationSender sender;

    public NotificationService(@Qualifier("email") NotificationSender sender) {
        System.out.println("[NotificationService] 생성자 — 주입된 sender: "
            + sender.getClass().getSimpleName());
        this.sender = sender;
    }

    @PostConstruct
    public void init() {
        System.out.println("[NotificationService] @PostConstruct");
    }

    public void notify(String to, String message) {
        sender.send(to, message);
    }
}
