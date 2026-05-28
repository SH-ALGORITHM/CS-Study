package domain;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 비즈니스 로직 계층.
 *
 * 2-2 부터는 sender (외부 전송) + logRepository (DB 기록) 둘 다 협력한다.
 *  - sender.send() : 실제 전송 (이번 학습에서는 println)
 *  - logRepository.save() : notification_log INSERT
 *
 * 두 의존성 모두 생성자 주입 — final 보장 + 테스트 시 mock 주입 용이 + 부팅 시점에 순환 참조 감지.
 *
 * 현재는 @Qualifier("email") 로 EmailSender 만 받는다.
 * 2-4 이후 Sms/Push/Slack 이 추가되면 NoUniqueBeanDefinitionException 이 생기므로 명시 지정 필요.
 */
@Service
public class NotificationService {

    private final NotificationSender sender;
    private final NotificationLogRepository logRepository;

    public NotificationService(
        @Qualifier("email") NotificationSender sender,
        NotificationLogRepository logRepository
    ) {
        System.out.println("[3] NotificationService constructor injection (sender + logRepository)");
        this.sender = sender;
        this.logRepository = logRepository;
    }

    @PostConstruct
    public void init() {
        System.out.println("[4] NotificationService @PostConstruct");
    }

    /**
     * 1) sender 로 실제 전송
     * 2) NotificationLog 만들어 repository 에 INSERT
     * 3) id 가 채워진 NotificationLog 반환
     */
    public NotificationLog notify(String to, String message) {
        sender.send(to, message);
        NotificationLog entry = NotificationLog.newEntry(to, message, sender.channel());
        return logRepository.save(entry);
    }

    @PreDestroy
    public void destroy() {
        System.out.println("[close] NotificationService @PreDestroy");
    }
}
