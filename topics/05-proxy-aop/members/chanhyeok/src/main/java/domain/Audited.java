package domain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 감사 로그 자작 어노테이션 — Stage4 양파 껍질 시연용.
 *
 * <p>@Order(2) — @DistributedLock(@Order(1)) 안쪽, 즉 락 잡은 후 비즈니스 직전에 기록.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    String action() default "";
}
