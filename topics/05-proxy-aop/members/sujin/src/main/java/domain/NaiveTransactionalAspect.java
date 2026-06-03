package domain;

import javax.sql.DataSource;
import java.sql.Connection;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

// @Aspect
// @Component
public class NaiveTransactionalAspect {

    private final DataSource dataSource;
    public NaiveTransactionalAspect(DataSource dataSource) { this.dataSource = dataSource; }

    @Around("@annotation(MyTransactional)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        try (Connection conn = dataSource.getConnection()) {   // ★ conn 1 — Repository 와 다른 커넥션!
            conn.setAutoCommit(false);
            System.out.println("[TX] begin (naive)");
            try {
                Object result = pjp.proceed();
                conn.commit();
                System.out.println("[TX] commit");
                return result;
            } catch (Throwable t) {
                conn.rollback();                                // ← conn 1 만 롤백 (효과 없음)
                System.out.println("[TX] rollback — " + t.getMessage());
                throw t;
            }
        }
    }
}
