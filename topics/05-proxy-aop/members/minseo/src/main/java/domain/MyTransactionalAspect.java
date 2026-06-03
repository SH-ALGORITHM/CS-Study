package domain;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Aspect
@Order(1)
public class MyTransactionalAspect {

    private static final ThreadLocal<Connection> TX_CONN = new ThreadLocal<>();

    private final DataSource dataSource;

    public MyTransactionalAspect(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Around("@annotation(domain.MyTransactional)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Connection conn = dataSource.getConnection();
        conn.setAutoCommit(false);
        TX_CONN.set(conn);
        System.out.println("[TX] begin — " + pjp.getSignature().getName());
        try {
            Object result = pjp.proceed();
            conn.commit();
            System.out.println("[TX] commit");
            return result;
        } catch (Throwable t) {
            conn.rollback();
            System.out.println("[TX] rollback — " + t.getMessage());
            throw t;
        } finally {
            TX_CONN.remove();
            try { conn.setAutoCommit(true); } catch (java.sql.SQLException ignore) {}
            try { conn.close(); } catch (java.sql.SQLException ignore) {}
        }
    }

    public static Connection currentConnection(DataSource ds) throws SQLException {
        Connection conn = TX_CONN.get();
        return (conn != null) ? conn : ds.getConnection();
    }

    public static boolean isTransactionActive() {
        return TX_CONN.get() != null;
    }
}
