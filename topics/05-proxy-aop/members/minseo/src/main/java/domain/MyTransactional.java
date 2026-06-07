package domain;

import java.lang.annotation.*;

/** 트랜잭션 마킹용 어노테이션 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MyTransactional {
}
