package domain;

import java.sql.Connection;
import javax.sql.DataSource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

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
                System.out.println("[Naive TX] commit — Aspect 의 conn 만 commit");
                return result;
            } catch (Throwable t) {
                conn.rollback();
                System.out.println("[Naive TX] rollback — Repository 의 conn 은 별개");
                throw t;
            }
        }
    }
}
