package domain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 트랜잭션 자작 어노테이션. 5 주차 STAGE 2-1 가장 중요한 학습.
 *
 * <p>{@link NaiveTransactionalAspect} (Step 1 함정) 와
 * {@link MyTransactionalAspect} (Step 3 ThreadLocal 해결) 가 동일 어노테이션 사용.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MyTransactional {}
