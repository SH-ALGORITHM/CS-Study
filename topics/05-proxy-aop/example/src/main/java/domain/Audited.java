package domain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 감사 로그 자작 어노테이션.
 *
 * <p>@Retention(RUNTIME) 가 없으면 컴파일 후 어노테이션 정보가 사라져 AOP 가 인식 못 함.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    String action() default "";
}
