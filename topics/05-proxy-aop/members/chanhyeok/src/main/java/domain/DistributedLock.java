package domain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 분산락 자작 어노테이션 — 3 주차 SETNX/Lua/finally 보일러플레이트를 한 줄로.
 *
 * <h3>사용 예</h3>
 * <pre>
 * &#64;DistributedLock(key = "wallet:#{fromId}", ttlSeconds = 5)
 * public void transfer(long fromId, long toId, BigDecimal amount) { ... }
 * </pre>
 *
 * <h3>SpEL</h3>
 * {@code key} 는 SpEL 표현식. 메서드 인자명을 그대로 참조 가능 (#fromId / #toId 등).
 * {@code -parameters} 컴파일 옵션 필요 (build.gradle 에 설정됨).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /** 락 키 (SpEL 지원). 예: "wallet:#{fromId}" */
    String key();

    /** TTL — 락 보유자가 죽어도 자동 해제. 기본 5 초 */
    int ttlSeconds() default 5;
}
