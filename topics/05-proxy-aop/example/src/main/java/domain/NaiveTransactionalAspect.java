package domain;

import javax.sql.DataSource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import java.sql.Connection;

/**
 * STAGE 2-1 Step 1 — 순진한 (틀린) 버전 Aspect.
 *
 * <p>Aspect 에서 `dataSource.getConnection()` 으로 새 conn 을 열고 begin/commit 하지만,
 * Repository 가 같은 호출 안에서 또 다른 conn 을 꺼내 쓰면 트랜잭션이 묶이지 않음.
 * 일부러 함정을 보여주는 코드.
 *
 * <p>{@link MyTransactionalAspect} 가 올바른 ThreadLocal 버전.
 */
@Aspect
public class NaiveTransactionalAspect {

    private final DataSource dataSource;

    public NaiveTransactionalAspect(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Around("@annotation(domain.MyTransactional)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            System.out.println("[Naive TX] begin — " + pjp.getSignature().getName());
            try {
                Object result = pjp.proceed();
                conn.commit();
                System.out.println("[Naive TX] commit — Aspect 의 conn 만 commit. Repository 의 conn 은 별개");
                return result;
            } catch (Throwable t) {
                conn.rollback();
                System.out.println("[Naive TX] rollback — Aspect 의 conn 만 rollback. Repository 의 변경은 그대로 남음");
                throw t;
            }
        }
    }
}
