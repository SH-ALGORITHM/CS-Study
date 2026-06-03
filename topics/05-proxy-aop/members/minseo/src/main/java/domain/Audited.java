package domain;

import java.lang.annotation.*;

/** 감사 로그 마킹용 어노테이션 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    String action(); // 어떤 동작인지 기록 (예: "TRANSFER")
}
