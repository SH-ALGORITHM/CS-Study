package domain;

import java.time.LocalDateTime;

/**
 * notification_log row 한 줄의 도메인 표현.
 *
 * id 는 DB 가 채우는 자동 증가 값이므로 INSERT 전에는 null.
 * 저장 후 RETURN_GENERATED_KEYS 로 받은 id 를 새 record 로 만들어 반환한다.
 */
public record NotificationLog(
    Long id,
    String to,
    String message,
    String channel,
    LocalDateTime sentAt
) {

    public static NotificationLog newEntry(String to, String message, String channel) {
        return new NotificationLog(null, to, message, channel, LocalDateTime.now());
    }

    public NotificationLog withId(long id) {
        return new NotificationLog(id, to, message, channel, sentAt);
    }
}
