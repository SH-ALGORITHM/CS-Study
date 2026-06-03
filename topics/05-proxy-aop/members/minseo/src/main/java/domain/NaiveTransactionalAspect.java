package domain;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import javax.sql.DataSource;
import java.sql.Connection;

/** STAGE 2-1 Step 1 — 함정이 있는 순진한 트랜잭션 Aspect */
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
